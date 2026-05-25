package com.kxh.aiagent.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.kxh.aiagent.agent.progress.AgentProgressEvent;
import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.ProgressHook;
import com.kxh.aiagent.agent.progress.StreamProgressEmitter;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

@RestController
@RequestMapping("/v2/agent")
public class ReactAgentController {

    private static final Logger log = LoggerFactory.getLogger(ReactAgentController.class);

    @Resource
    private ReactAgent generalAgent;

    @Resource
    private ReactAgent investAgent;

    @Resource
    private SequentialAgent investReportPipeline;

    @Resource
    private ProgressEventBus progressEventBus;

    /** 活跃请求的 SSE 连接，用于取消 */
    private final ConcurrentHashMap<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    // ==================== 通用Agent ====================

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String message,
                           @RequestParam(required = false) String threadId) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        StreamProgressEmitter progress = new StreamProgressEmitter(emitter);
        String requestId = UUID.randomUUID().toString();
        activeEmitters.put(requestId, emitter);
        progressEventBus.register(requestId, progress);

        String finalThreadId = threadId;

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(finalThreadId)
                        .addMetadata(ProgressHook.CONFIG_KEY, requestId)
                        .build();

                progress.agentStart("kxhAgent");
                AssistantMessage result = generalAgent.call(message, config);
                progress.sendRaw(result.getText());
                progress.agentDone("kxhAgent");
                progress.complete();
            } catch (Exception e) {
                log.error("Agent执行异常: {}", e.getMessage(), e);
                progress.agentError("kxhAgent", e.getMessage());
                progress.error(e);
            } finally {
                cleanup(requestId);
            }
        });

        emitter.onCompletion(() -> cleanup(requestId));
        emitter.onTimeout(() -> cleanup(requestId));
        return emitter;
    }

    // ==================== 投资Agent ====================

    @GetMapping(value = "/invest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter invest(@RequestParam String message,
                             @RequestParam(required = false) String threadId) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        StreamProgressEmitter progress = new StreamProgressEmitter(emitter);
        String requestId = UUID.randomUUID().toString();
        activeEmitters.put(requestId, emitter);
        progressEventBus.register(requestId, progress);

        String finalThreadId = threadId;

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(finalThreadId)
                        .addMetadata(ProgressHook.CONFIG_KEY, requestId)
                        .build();

                progress.agentStart("InvestAgent");
                AssistantMessage result = investAgent.call(message, config);
                progress.sendRaw(result.getText());
                progress.agentDone("InvestAgent");
                progress.complete();
            } catch (Exception e) {
                log.error("InvestAgent执行异常: {}", e.getMessage(), e);
                progress.agentError("InvestAgent", e.getMessage());
                progress.error(e);
            } finally {
                cleanup(requestId);
            }
        });

        emitter.onCompletion(() -> cleanup(requestId));
        emitter.onTimeout(() -> cleanup(requestId));
        return emitter;
    }

    // ==================== 投资研报流水线 ====================

    @GetMapping(value = "/invest/analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter investAnalysis(@RequestParam String message,
                                     @RequestParam(required = false) String threadId) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(600_000L);
        StreamProgressEmitter progress = new StreamProgressEmitter(emitter);
        String requestId = UUID.randomUUID().toString();
        activeEmitters.put(requestId, emitter);
        progressEventBus.register(requestId, progress);

        String finalThreadId = threadId;

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(finalThreadId)
                        .addMetadata(ProgressHook.CONFIG_KEY, requestId)
                        .build();

                // 发送流水线结构信息
                progress.progress("投资研报流水线",
                        "4阶段流水线启动: 数据采集 → 8Agent并行分析 → 投资建议 → 研报生成");

                CountDownLatch latch = new CountDownLatch(1);

                investReportPipeline.streamMessages(message, config)
                        .subscribe(
                                msg -> {
                                    String text = msg.getText();
                                    if (text != null && !text.isBlank()) {
                                        progress.sendRaw(text);
                                    }
                                },
                                error -> {
                                    log.error("流水线执行异常: {}", error.getMessage(), error);
                                    progress.agentError("投资研报流水线", error.getMessage());
                                    latch.countDown();
                                },
                                () -> {
                                    progress.complete();
                                    latch.countDown();
                                }
                        );

                latch.await();
            } catch (Exception e) {
                log.error("投资研报流水线执行异常: {}", e.getMessage(), e);
                progress.agentError("投资研报流水线", e.getMessage());
                progress.error(e);
            } finally {
                cleanup(requestId);
            }
        });

        emitter.onCompletion(() -> cleanup(requestId));
        emitter.onTimeout(() -> cleanup(requestId));
        return emitter;
    }

    // ==================== 中断 ====================

    @GetMapping("/cancel")
    public String cancel(@RequestParam(required = false) String threadId,
                         @RequestParam(required = false) String requestId) {
        log.info("中断请求: threadId={}, requestId={}", threadId, requestId);

        if (requestId != null) {
            SseEmitter emitter = activeEmitters.get(requestId);
            if (emitter != null) {
                emitter.complete();
                cleanup(requestId);
            }
        }

        if (threadId != null) {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        .build();
                generalAgent.interrupt(config);
                investAgent.interrupt(config);
            } catch (Exception e) {
                log.error("中断失败: {}", e.getMessage(), e);
                return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
            }
        }

        return "{\"status\":\"ok\",\"message\":\"已发送中断信号\"}";
    }

    /** 获取活跃 Agent 数量 */
    @GetMapping("/status")
    public String status() {
        return "{\"activeConnections\":" + activeEmitters.size() + "}";
    }

    private void cleanup(String requestId) {
        progressEventBus.unregister(requestId);
        activeEmitters.remove(requestId);
    }
}
