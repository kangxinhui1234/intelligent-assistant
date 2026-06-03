package com.kxh.aiagent.ops.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncidentEvent(
        String incidentId,
        String fingerprint,
        String source,
        String serviceName,
        Severity severity,
        Instant occurredAt,
        Map<String, Object> raw,
        String summary
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String incidentId = UUID.randomUUID().toString();
        private String fingerprint;
        private String source;
        private String serviceName;
        private Severity severity = Severity.P1;
        private Instant occurredAt = Instant.now();
        private Map<String, Object> raw = Map.of();
        private String summary;

        public Builder incidentId(String v) { this.incidentId = v; return this; }
        public Builder fingerprint(String v) { this.fingerprint = v; return this; }
        public Builder source(String v) { this.source = v; return this; }
        public Builder serviceName(String v) { this.serviceName = v; return this; }
        public Builder severity(Severity v) { this.severity = v; return this; }
        public Builder occurredAt(Instant v) { this.occurredAt = v; return this; }
        public Builder raw(Map<String, Object> v) { this.raw = v; return this; }
        public Builder summary(String v) { this.summary = v; return this; }

        public IncidentEvent build() {
            return new IncidentEvent(incidentId, fingerprint, source, serviceName,
                    severity, occurredAt, raw, summary);
        }
    }
}
