package com.kxh.aiagent.ops.source;

import com.kxh.aiagent.ops.dedup.FingerprintGenerator;
import com.kxh.aiagent.ops.model.IncidentEvent;
import com.kxh.aiagent.ops.model.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 接入层 —— 极简实现。
 *
 * 仅做两件事:
 * 1) 生成指纹(归一化全文哈希) — 给去重器用
 * 2) 粗略的严重度判断(只看关键词) — 给前端/日志显示用
 *
 * 服务名、错误类、时间、TraceId 等结构化字段一律不做提取,交给 AlertParserAgent。
 */
@Component
public class WebhookAlertSource implements AlertSource {

    @Autowired
    private FingerprintGenerator fingerprintGenerator;

    @Override
    public String sourceType() {
        return "webhook";
    }

    public IncidentEvent toIncident(RawAlert raw) {
        String subject = raw.subject() == null ? "" : raw.subject();
        String body = raw.body() == null ? "" : raw.body();
        String fullText = subject + "\n" + body;

        String fingerprint = fingerprintGenerator.fromAlertText(fullText);
        Severity severity = guessSeverity(fullText);

        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("subject", subject);
        rawMap.put("body", body);
        if (raw.headers() != null) rawMap.putAll(raw.headers());

        // serviceName 在指纹之后,只是用于日志和展示,真实服务名由 AlertParserAgent 解析输出
        String serviceHint = serviceHintFromHeader(raw);

        return IncidentEvent.builder()
                .source(sourceType())
                .serviceName(serviceHint)
                .severity(severity)
                .fingerprint(fingerprint)
                .occurredAt(raw.receivedAt() != null ? raw.receivedAt() : Instant.now())
                .raw(rawMap)
                .summary(buildSummary(subject, body))
                .build();
    }

    /** 严重度只看明显关键词,搞不清就 P1。真实严重度可由 AlertParserAgent 修正 */
    private Severity guessSeverity(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("CRITICAL") || upper.contains("FATAL") || upper.contains("P0")
                || text.contains("严重")) return Severity.P0;
        if (upper.contains("P2") || text.contains("低级")) return Severity.P2;
        return Severity.P1;
    }

    /** 仅当请求方在 headers 里显式指定 service 时才采用 */
    private String serviceHintFromHeader(RawAlert raw) {
        if (raw.headers() != null && raw.headers().get("service") != null) {
            return raw.headers().get("service").toString();
        }
        return "pending";  // 表示等待 AlertParserAgent 解析
    }

    private String buildSummary(String subject, String body) {
        if (subject != null && !subject.isBlank()) {
            return subject.length() > 100 ? subject.substring(0, 100) : subject;
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed;
    }
}
