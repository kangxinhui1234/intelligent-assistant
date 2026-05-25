package com.kxh.aiagent.agent.progress;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ProgressHook extends AgentHook {

    private static final Logger log = LoggerFactory.getLogger(ProgressHook.class);

    public static final String CONFIG_KEY = "_progressRequestId";

    private final ProgressEventBus eventBus;

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
            log.debug("Agent start: {} (request: {})", agentName, requestId);
            eventBus.publish(requestId, AgentProgressEvent.agentStart(agentName));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        String requestId = getRequestId(config);
        String agentName = getAgentName();
        if (requestId != null && agentName != null) {
            log.debug("Agent done: {} (request: {})", agentName, requestId);
            eventBus.publish(requestId, AgentProgressEvent.agentDone(agentName));
        }
        return CompletableFuture.completedFuture(null);
    }

    private String getRequestId(RunnableConfig config) {
        if (config == null) {
            return null;
        }
        return config.metadata(CONFIG_KEY)
                .map(Object::toString)
                .orElse(null);
    }
}
