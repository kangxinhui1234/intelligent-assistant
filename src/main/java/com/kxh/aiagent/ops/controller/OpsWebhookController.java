package com.kxh.aiagent.ops.controller;

import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.StreamProgressEmitter;
import com.kxh.aiagent.ops.mq.IncidentStreamConfig;
import com.kxh.aiagent.ops.mq.IncidentStreamPublisher;
import com.kxh.aiagent.ops.service.IncidentProcessor;
import com.kxh.aiagent.ops.source.RawAlert;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/ops")
public class OpsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(OpsWebhookController.class);

    @Resource private IncidentProcessor processor;
    @Resource private ProgressEventBus progressEventBus;
    @Resource private IncidentStreamPublisher streamPublisher;
    @Resource private StringRedisTemplate redis;

    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    @PostMapping(value = "/alert/webhook", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleAlertWebhook(@RequestBody Map<String, Object> payload) {
        String subject = (String) payload.getOrDefault("subject", "");
        String body = (String) payload.getOrDefault("body", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) payload.getOrDefault("headers", new HashMap<>());
        RawAlert raw = new RawAlert("webhook", subject, body, headers, Instant.now());

        SseEmitter emitter = new SseEmitter(300_000L);
        StreamProgressEmitter progress = new StreamProgressEmitter(emitter);
        String requestId = UUID.randomUUID().toString();
        activeEmitters.put(requestId, emitter);
        progressEventBus.register(requestId, progress);

        emitter.onCompletion(() -> activeEmitters.remove(requestId));
        emitter.onTimeout(() -> activeEmitters.remove(requestId));

        var outcome = processor.process(raw, progress, requestId);
        log.info("Webhook 接收: status={} incidentId={} detail={}",
                outcome.status(),
                outcome.incident() == null ? "-" : outcome.incident().incidentId(),
                outcome.detail());
        return emitter;
    }

    /**
     * 异步入口 — 走 Redis Stream MQ。
     * 适合: 告警批量推送 / 客户系统集成 / 高 QPS 场景。
     * 同步入口 /alert/webhook 适合: 调试 / 单条手工触发 / 想看 SSE 实时进度。
     */
    @PostMapping("/alert/queue")
    public Map<String, Object> queueAlert(@RequestBody Map<String, Object> payload) {
        String subject = (String) payload.getOrDefault("subject", "");
        String body = (String) payload.getOrDefault("body", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) payload.getOrDefault("headers", new HashMap<>());
        RawAlert raw = new RawAlert("webhook-async", subject, body, headers, Instant.now());

        String requestId = UUID.randomUUID().toString();
        String streamId = streamPublisher.publish(raw, requestId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", streamId != null);
        resp.put("streamId", streamId);
        resp.put("requestId", requestId);
        resp.put("hint", streamId == null
                ? "publish 失败 (Redis 未配置?), 退化走 /alert/webhook"
                : "已入队,Consumer 异步消费;进度看后台日志");
        return resp;
    }

    /** 查询 Stream 健康状态 + Pending Entries List 长度 */
    @GetMapping("/mq/stats")
    public Map<String, Object> mqStats() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Long streamLen = redis.opsForStream().size(IncidentStreamConfig.STREAM_KEY);
            Long dlqLen    = redis.opsForStream().size(IncidentStreamConfig.DLQ_KEY);
            PendingMessagesSummary pending = redis.opsForStream().pending(
                    IncidentStreamConfig.STREAM_KEY, IncidentStreamConfig.CONSUMER_GROUP);
            resp.put("stream", IncidentStreamConfig.STREAM_KEY);
            resp.put("streamLength", streamLen == null ? 0 : streamLen);
            resp.put("dlqLength", dlqLen == null ? 0 : dlqLen);
            resp.put("group", IncidentStreamConfig.CONSUMER_GROUP);
            resp.put("pendingTotal", pending == null ? 0 : pending.getTotalPendingMessages());
            resp.put("consumers", pending == null ? Map.of()
                    : pending.getPendingMessagesPerConsumer().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue)));
        } catch (Exception e) {
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    /** DLQ 内容查看 (最近 20 条) */
    @GetMapping("/mq/dlq")
    public List<Map<String, Object>> dlqList() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                    .range(IncidentStreamConfig.DLQ_KEY, Range.<String>unbounded(),
                            Limit.unlimited().count(20));
            if (records != null) {
                for (MapRecord<String, Object, Object> r : records) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId() == null ? null : r.getId().getValue());
                    r.getValue().forEach((k, v) -> m.put(String.valueOf(k), v));
                    out.add(m);
                }
            }
        } catch (Exception e) {
            out.add(Map.of("error", e.getMessage()));
        }
        return out;
    }

    @PostMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("{\"status\":\"ok\",\"module\":\"ops\"}");
    }
}
