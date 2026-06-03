package com.kxh.aiagent.agent.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

@Component
public class ProgressEventBus {

    private static final Logger log = LoggerFactory.getLogger(ProgressEventBus.class);

    private final ConcurrentHashMap<String, StreamProgressEmitter> listeners = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, AgentProgressEvent>> globalListeners = new CopyOnWriteArrayList<>();

    public void register(String requestId, StreamProgressEmitter emitter) {
        if (emitter != null) {
            listeners.put(requestId, emitter);
        }
        log.debug("Progress listener registered: {}", requestId);
    }

    public void unregister(String requestId) {
        listeners.remove(requestId);
        log.debug("Progress listener unregistered: {}", requestId);
    }

    /** 全局监听器: 例如 IncidentPersister 用于把所有 agent_done 事件落库 */
    public void addGlobalListener(BiConsumer<String, AgentProgressEvent> listener) {
        globalListeners.add(listener);
    }

    public void publish(String requestId, AgentProgressEvent event) {
        StreamProgressEmitter emitter = listeners.get(requestId);
        if (emitter != null) {
            log.info("SSE event → {} {} (request: {})", event.type(), event.agentName(), requestId);
            emitter.send(event);
        } else {
            log.debug("SSE event (no client listener): requestId={}, event={}", requestId, event.type());
        }
        // 全局监听者总是收到
        for (BiConsumer<String, AgentProgressEvent> l : globalListeners) {
            try {
                l.accept(requestId, event);
            } catch (Exception e) {
                log.warn("globalListener 处理异常: {}", e.getMessage());
            }
        }
    }
}
