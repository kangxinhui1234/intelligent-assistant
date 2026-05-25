package com.kxh.aiagent.agent.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public class StreamProgressEmitter {

    private static final Logger log = LoggerFactory.getLogger(StreamProgressEmitter.class);

    private final SseEmitter emitter;

    public StreamProgressEmitter(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void send(AgentProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(event.toJson()));
        } catch (IOException e) {
            log.debug("SSE send failed, client may have disconnected: {}", e.getMessage());
        }
    }

    public void agentStart(String agentName) {
        send(AgentProgressEvent.agentStart(agentName));
    }

    public void agentStart(String agentName, int stage, int totalStages) {
        send(AgentProgressEvent.agentStart(agentName, String.valueOf(stage), totalStages));
    }

    public void agentMessage(String agentName, String content) {
        send(AgentProgressEvent.agentMessage(agentName, content));
    }

    public void agentDone(String agentName) {
        send(AgentProgressEvent.agentDone(agentName));
    }

    public void agentDone(String agentName, int stage, int totalStages) {
        send(AgentProgressEvent.agentDone(agentName, String.valueOf(stage), totalStages));
    }

    public void agentError(String agentName, String error) {
        send(AgentProgressEvent.agentError(agentName, error));
    }

    public void progress(String agentName, String message) {
        send(AgentProgressEvent.progress(agentName, message));
    }

    public void sendRaw(String content) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(content));
        } catch (IOException e) {
            log.debug("SSE raw send failed: {}", e.getMessage());
        }
    }

    public void complete() {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException e) {
            log.debug("SSE complete failed: {}", e.getMessage());
        }
    }

    public void error(Throwable e) {
        try {
            emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
        } catch (IOException ignored) {
        }
        emitter.completeWithError(e);
    }
}
