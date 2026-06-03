package com.kxh.aiagent.ops.service;

import com.kxh.aiagent.agent.progress.ProgressEventBus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 把 ProgressEventBus 的事件桥接到 IncidentPersister。
 * requestId ↔ incidentId 的映射在 IncidentProcessor 启动流水线前注册。
 * <p>
 * 每个 agent_done 事件触发一次 investigation_log 写入。
 */
@Component
public class PersistenceWiring {

    private static final Logger log = LoggerFactory.getLogger(PersistenceWiring.class);

    @Resource private ProgressEventBus progressEventBus;
    @Resource private IncidentPersister persister;

    private final ConcurrentHashMap<String, RequestContext> contexts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        progressEventBus.addGlobalListener((requestId, event) -> {
            RequestContext ctx = contexts.get(requestId);
            if (ctx == null) return;
            if ("agent_done".equals(event.type())) {
                persister.saveInvestigationLog(
                        ctx.incidentId,
                        event.agentName(),
                        ctx.lastAgentMessage.remove(event.agentName()),
                        event.durationMs(),
                        event.promptTokens(),
                        event.completionTokens(),
                        event.totalTokens()
                );
            } else if ("agent_message".equals(event.type()) && event.content() != null) {
                ctx.lastAgentMessage.put(event.agentName(), event.content());
            }
        });
        log.info("PersistenceWiring 已注册全局监听");
    }

    /** IncidentProcessor 启动流水线前调用 */
    public void track(String requestId, String incidentId) {
        contexts.put(requestId, new RequestContext(incidentId));
    }

    public void untrack(String requestId) {
        contexts.remove(requestId);
    }

    private static class RequestContext {
        final String incidentId;
        final ConcurrentHashMap<String, String> lastAgentMessage = new ConcurrentHashMap<>();
        RequestContext(String incidentId) { this.incidentId = incidentId; }
    }
}
