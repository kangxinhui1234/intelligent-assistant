package com.kxh.aiagent.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/v2/agent")
public class ReactAgentController {

    @Resource
    private ReactAgent generalAgent;

    @Resource
    private ReactAgent investAgent;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String message,
                           @RequestParam(required = false) String threadId) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        String finalThreadId = threadId;

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(finalThreadId)
                        .build();
                AssistantMessage result = generalAgent.call(message, config);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(result.getText()));
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("Agent执行异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping(value = "/invest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter invest(@RequestParam String message,
                             @RequestParam(required = false) String threadId) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        String finalThreadId = threadId;

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(finalThreadId)
                        .build();
                AssistantMessage result = investAgent.call(message, config);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(result.getText()));
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("InvestAgent执行异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
