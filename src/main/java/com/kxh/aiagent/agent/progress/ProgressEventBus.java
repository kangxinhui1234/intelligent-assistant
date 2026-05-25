package com.kxh.aiagent.agent.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgressEventBus {

    private static final Logger log = LoggerFactory.getLogger(ProgressEventBus.class);

    private final ConcurrentHashMap<String, StreamProgressEmitter> listeners = new ConcurrentHashMap<>();

    public void register(String requestId, StreamProgressEmitter emitter) {
        listeners.put(requestId, emitter);
        log.debug("Progress listener registered: {}", requestId);
    }

    public void unregister(String requestId) {
        listeners.remove(requestId);
        log.debug("Progress listener unregistered: {}", requestId);
    }

    public void publish(String requestId, AgentProgressEvent event) {
        StreamProgressEmitter emitter = listeners.get(requestId);
        if (emitter != null) {
            emitter.send(event);
        }
    }
}
