package com.kxh.aiagent.ops.controller;

import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.StreamProgressEmitter;
import com.kxh.aiagent.ops.service.IncidentProcessor;
import com.kxh.aiagent.ops.source.RawAlert;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/ops")
public class OpsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(OpsWebhookController.class);

    @Resource private IncidentProcessor processor;
    @Resource private ProgressEventBus progressEventBus;

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

    @PostMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("{\"status\":\"ok\",\"module\":\"ops\"}");
    }
}
