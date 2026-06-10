package com.kxh.aiagent.ops.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.ops.source.RawAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 告警事件发布器 — 各告警源 (Webhook / Email IMAP / 主动巡检) 把 RawAlert 投递到 Redis Stream。
 * 消息字段一律字符串,便于 redis-cli XRANGE 调试。
 */
@Component
public class IncidentStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentStreamPublisher.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private StringRedisTemplate redis;

    /** 发布到主流,返回 streamId (XADD 返回值) */
    public String publish(RawAlert raw, String requestId) {
        if (redis == null) {
            log.warn("[Stream] Redis 未配置, publish 跳过 source={}", raw.source());
            return null;
        }
        String rid = requestId == null ? UUID.randomUUID().toString() : requestId;
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("source", str(raw.source()));
            fields.put("subject", str(raw.subject()));
            fields.put("body", str(raw.body()));
            fields.put("headers", raw.headers() == null ? "{}" : mapper.writeValueAsString(raw.headers()));
            fields.put("receivedAt", raw.receivedAt() == null ? "" : raw.receivedAt().toString());
            fields.put("requestId", rid);
            fields.put("retryCount", "0");

            MapRecord<String, String, String> record = MapRecord.create(IncidentStreamConfig.STREAM_KEY, fields);
            RecordId id = redis.opsForStream().add(record);
            String idStr = id == null ? null : id.getValue();
            log.info("[Stream] 已发布 streamId={} source={} requestId={}", idStr, raw.source(), rid);
            return idStr;
        } catch (Exception e) {
            log.error("[Stream] 发布失败 source={}: {}", raw.source(), e.getMessage(), e);
            return null;
        }
    }

    private String str(String s) { return s == null ? "" : s; }
}
