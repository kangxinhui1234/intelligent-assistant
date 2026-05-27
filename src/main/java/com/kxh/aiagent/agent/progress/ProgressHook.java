package com.kxh.aiagent.agent.progress;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ProgressHook extends AgentHook {

    private static final Logger log = LoggerFactory.getLogger(ProgressHook.class);

    public static final String CONFIG_KEY = "_progressRequestId";

    private final ProgressEventBus eventBus;
    private final ConcurrentHashMap<String, Integer> messageCountBefore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startTimestamps = new ConcurrentHashMap<>();

    public ProgressHook(ProgressEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        String name = getAgentName();
        return name != null ? "progress_" + name : "progressHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        String requestId = getRequestId(config);
        String agentName = getAgentName();
        if (requestId != null && agentName != null) {
            String trackKey = requestId + ":" + agentName;
            int count = getMessageCount(state);
            messageCountBefore.put(trackKey, count);
            startTimestamps.put(trackKey, System.currentTimeMillis());

            log.info("▶ Agent start: {} (request: {}, msgCount: {})", agentName, requestId, count);
            eventBus.publish(requestId, AgentProgressEvent.agentStart(agentName));
        } else {
            log.warn("ProgressHook.beforeAgent skipped: requestId={}, agentName={}", requestId, agentName);
        }
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        String requestId = getRequestId(config);
        String agentName = getAgentName();
        if (requestId != null && agentName != null) {
            String trackKey = requestId + ":" + agentName;

            // Calculate duration
            long startTime = startTimestamps.getOrDefault(trackKey, System.currentTimeMillis());
            long durationMs = System.currentTimeMillis() - startTime;
            startTimestamps.remove(trackKey);

            // Extract agent output
            int beforeCount = messageCountBefore.getOrDefault(trackKey, 0);
            messageCountBefore.remove(trackKey);
            String output = extractNewMessages(state, beforeCount);

            // Try to extract token usage from state
            Integer promptTokens = null, completionTokens = null, totalTokens = null;
            try {
                Object tokenObj = state.data().get("_TOKEN_USAGE_");
                if (tokenObj instanceof Usage usage) {
                    promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : null;
                    completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : null;
                    totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : null;
                } else if (tokenObj instanceof Map<?, ?> usageMap) {
                    promptTokens = toInt(usageMap.get("promptTokens"));
                    completionTokens = toInt(usageMap.get("completionTokens"));
                    totalTokens = toInt(usageMap.get("totalTokens"));
                }
            } catch (Exception e) {
                log.debug("Token usage extraction failed for {}: {}", agentName, e.getMessage());
            }

            if (output != null && !output.isBlank()) {
                eventBus.publish(requestId, AgentProgressEvent.agentMessage(agentName, output));
            }

            log.info("✓ Agent done: {} (request: {}, duration: {}ms, tokens: {}, outputLen: {})",
                    agentName, requestId, durationMs,
                    totalTokens != null ? totalTokens : "N/A",
                    output != null ? output.length() : 0);

            eventBus.publish(requestId, AgentProgressEvent.agentDone(
                    agentName, durationMs, promptTokens, completionTokens, totalTokens));
        } else {
            log.warn("ProgressHook.afterAgent skipped: requestId={}, agentName={}", requestId, agentName);
        }
        return CompletableFuture.completedFuture(Map.of());
    }

    private String extractNewMessages(OverAllState state, int beforeCount) {
        try {
            Object messagesObj = state.data().get("messages");
            if (!(messagesObj instanceof List<?> messages)) return null;

            StringBuilder sb = new StringBuilder();
            for (int i = beforeCount; i < messages.size(); i++) {
                Object msg = messages.get(i);
                if (msg instanceof AssistantMessage am && am.getText() != null) {
                    sb.append(am.getText());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("Failed to extract agent output: {}", e.getMessage());
            return null;
        }
    }

    private int getMessageCount(OverAllState state) {
        try {
            Object obj = state.data().get("messages");
            return (obj instanceof List<?> list) ? list.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        return null;
    }

    private String getRequestId(RunnableConfig config) {
        if (config == null) return null;
        return config.metadata(CONFIG_KEY)
                .map(Object::toString)
                .orElse(null);
    }
}
