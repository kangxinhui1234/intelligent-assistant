package com.kxh.aiagent.agent.progress;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentProgressEvent(
        String type,
        String agentName,
        String content,
        String stage,
        int totalStages,
        long timestamp
) {
    private static final ObjectMapper mapper = new ObjectMapper();

    public AgentProgressEvent {
        if (timestamp == 0) {
            timestamp = Instant.now().toEpochMilli();
        }
    }

    public static AgentProgressEvent agentStart(String agentName, String stage, int totalStages) {
        return new AgentProgressEvent("agent_start", agentName, null, stage, totalStages, 0);
    }

    public static AgentProgressEvent agentStart(String agentName) {
        return new AgentProgressEvent("agent_start", agentName, null, null, 0, 0);
    }

    public static AgentProgressEvent agentMessage(String agentName, String content) {
        return new AgentProgressEvent("agent_message", agentName, content, null, 0, 0);
    }

    public static AgentProgressEvent agentDone(String agentName, String stage, int totalStages) {
        return new AgentProgressEvent("agent_done", agentName, null, stage, totalStages, 0);
    }

    public static AgentProgressEvent agentDone(String agentName) {
        return new AgentProgressEvent("agent_done", agentName, null, null, 0, 0);
    }

    public static AgentProgressEvent agentError(String agentName, String error) {
        return new AgentProgressEvent("agent_error", agentName, error, null, 0, 0);
    }

    public static AgentProgressEvent progress(String agentName, String message) {
        return new AgentProgressEvent("progress", agentName, message, null, 0, 0);
    }

    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"content\":\"json serialize failed\"}";
        }
    }
}
