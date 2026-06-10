package com.kxh.aiagent.ops.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.ops.service.IncidentProcessor;
import com.kxh.aiagent.ops.source.RawAlert;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Stream 消费者 — 拉到一条 RawAlert 就交给 {@link IncidentProcessor#process} 跑全流程。
 * <p>
 * 重试 / DLQ 策略:
 *  - 处理成功 → XACK
 *  - 处理失败 → 不 ACK,记录 retryCount;到达 {@link #maxRetries} 后写入 DLQ 并 XACK
 *  - DLQ 形态: 同 schema + error_msg + final_failed_at
 * <p>
 * 横向扩展: 多实例共享 group "ops-incident-group",同一条消息只会被一个实例消费。
 */
@Component
public class IncidentStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(IncidentStreamConsumer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired private IncidentProcessor processor;
    @Autowired private StringRedisTemplate redis;

    @Value("${ops.mq.consumer-name:}")
    private String configuredConsumerName;

    @Value("${ops.mq.max-retries:3}")
    private int maxRetries;

    private String consumerName;

    @PostConstruct
    void init() {
        if (configuredConsumerName == null || configuredConsumerName.isBlank()) {
            String pid = ManagementFactory.getRuntimeMXBean().getName();   // pid@host
            consumerName = "consumer-" + pid;
        } else {
            consumerName = configuredConsumerName;
        }
    }

    public String consumerName() { return consumerName; }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        RecordId id = message.getId();
        Map<String, String> fields = message.getValue();
        int retryCount = parseInt(fields.get("retryCount"), 0);
        String requestId = fields.getOrDefault("requestId", "");

        try {
            RawAlert raw = restore(fields);
            log.info("[Consumer] 收到 streamId={} source={} requestId={} retry={}",
                    id.getValue(), raw.source(), requestId, retryCount);

            // MQ Consumer 不再串 SSE 实时进度(已经异步了),传 null。
            processor.process(raw, null, requestId);

            // 成功 — ACK
            redis.opsForStream().acknowledge(IncidentStreamConfig.STREAM_KEY,
                    IncidentStreamConfig.CONSUMER_GROUP, id);
            log.info("[Consumer] ACK streamId={}", id.getValue());

        } catch (Exception e) {
            log.error("[Consumer] 处理失败 streamId={} retry={}: {}",
                    id.getValue(), retryCount, e.getMessage(), e);
            handleFailure(id, fields, retryCount, e.getMessage());
        }
    }

    /**
     * 失败处理:
     *  - retryCount < maxRetries: 不 ACK,Redis 会保留 PEL (pending entries list),后续可 reclaim
     *    简化版直接 XADD 一条新消息(retryCount+1)并 ACK 旧的 — 易于 redis-cli 调试
     *  - 达到上限 → 写 DLQ + ACK
     */
    private void handleFailure(RecordId id, Map<String, String> fields, int retryCount, String error) {
        try {
            if (retryCount + 1 >= maxRetries) {
                // 写 DLQ
                Map<String, String> dlqFields = new HashMap<>(fields);
                dlqFields.put("error_msg", error == null ? "unknown" : error.substring(0, Math.min(error.length(), 2000)));
                dlqFields.put("final_failed_at", Instant.now().toString());
                dlqFields.put("original_stream_id", id.getValue());
                redis.opsForStream().add(MapRecord.create(IncidentStreamConfig.DLQ_KEY, dlqFields));
                log.warn("[Consumer] 重试已达上限 ({}) → 转 DLQ streamId={}", maxRetries, id.getValue());
            } else {
                // 重新入队
                Map<String, String> retryFields = new HashMap<>(fields);
                retryFields.put("retryCount", String.valueOf(retryCount + 1));
                retryFields.put("last_error", error == null ? "" : error.substring(0, Math.min(error.length(), 500)));
                redis.opsForStream().add(MapRecord.create(IncidentStreamConfig.STREAM_KEY, retryFields));
                log.info("[Consumer] 重投 streamId={} 新 retryCount={}", id.getValue(), retryCount + 1);
            }
            // 旧消息 ACK 掉避免重复消费
            redis.opsForStream().acknowledge(IncidentStreamConfig.STREAM_KEY,
                    IncidentStreamConfig.CONSUMER_GROUP, id);
        } catch (Exception e) {
            log.error("[Consumer] 失败处理本身异常 streamId={}: {}", id.getValue(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private RawAlert restore(Map<String, String> fields) {
        String source = fields.getOrDefault("source", "unknown");
        String subject = fields.getOrDefault("subject", "");
        String body = fields.getOrDefault("body", "");
        Map<String, Object> headers = new HashMap<>();
        try {
            String h = fields.getOrDefault("headers", "{}");
            if (h != null && !h.isBlank()) {
                headers = mapper.readValue(h, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {}
        Instant receivedAt;
        try {
            String r = fields.get("receivedAt");
            receivedAt = (r == null || r.isBlank()) ? Instant.now() : Instant.parse(r);
        } catch (Exception e) {
            receivedAt = Instant.now();
        }
        return new RawAlert(source, subject, body, headers, receivedAt);
    }

    private int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
