package com.kxh.aiagent.ops.source;

import java.time.Instant;
import java.util.Map;

public record RawAlert(
        String source,
        String subject,
        String body,
        Map<String, Object> headers,
        Instant receivedAt
) {}
