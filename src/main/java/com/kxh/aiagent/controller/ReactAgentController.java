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

    @Resource
    private org.springframework.ai.chat.model.ChatModel dashscopeChatModel;

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

                generalAgent.call(message, config);
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

                investAgent.call(message, config);
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

                log.info("═══ 投资研报流水线启动: threadId={}, requestId={} ═══", finalThreadId, requestId);
                progress.progress("投资研报流水线",
                        "4阶段流水线启动: 数据采集 → 8Agent并行分析 → 投资建议 → 研报生成\nthreadId: " + finalThreadId);

                CountDownLatch latch = new CountDownLatch(1);

                investReportPipeline.streamMessages(message, config)
                        .subscribe(
                                msg -> {},
                                error -> {
                                    log.error("流水线执行异常: {}", error.getMessage(), error);
                                    progress.agentError("投资研报流水线", error.getMessage());
                                    latch.countDown();
                                },
                                () -> {
                                    log.info("═══ 投资研报流水线完成 ═══");
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

    // ==================== 模型诊断 ====================

    @GetMapping("/ping")
    public String ping() {
        try {
            org.springframework.ai.chat.prompt.Prompt prompt =
                    new org.springframework.ai.chat.prompt.Prompt("回复'pong'");
            org.springframework.ai.chat.model.ChatResponse response = dashscopeChatModel.call(prompt);
            return "OK: " + response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("ChatModel call failed", e);
            return "FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @GetMapping("/ping2")
    public String ping2() {
        try {
            ReactAgent bareAgent = ReactAgent.builder()
                    .name("bare")
                    .model(dashscopeChatModel)
                    .tools(new org.springframework.ai.tool.ToolCallback[0])
                    .systemPrompt("回复简洁的答案")
                    .instruction("")
                    .enableLogging(true)
                    .build();
            var result = bareAgent.call("回复'bare-pong'");
            return "OK: " + result.getText();
        } catch (Exception e) {
            log.error("ReactAgent bare call failed", e);
            return "FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @GetMapping("/ping3")
    public String ping3() {
        try {
            ReactAgent agent = ReactAgent.builder()
                    .name("test")
                    .model(dashscopeChatModel)
                    .tools(new org.springframework.ai.tool.ToolCallback[0])
                    .systemPrompt("回复简洁的答案")
                    .instruction("")
                    .hooks(new ProgressHook(progressEventBus))
                    .interceptors(
                            com.alibaba.cloud.ai.graph.agent.interceptor.modelretry.ModelRetryInterceptor.builder()
                                    .maxAttempts(3).build()
                    )
                    .enableLogging(true)
                    .build();
            var result = agent.call("回复'ping3-ok'");
            return "OK: " + result.getText();
        } catch (Exception e) {
            log.error("ping3 failed", e);
            return "FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @GetMapping("/ping4")
    public String ping4() {
        try {
            // 只测 ProgressHook，无 ModelRetryInterceptor
            ReactAgent agent = ReactAgent.builder()
                    .name("test")
                    .model(dashscopeChatModel)
                    .tools(new org.springframework.ai.tool.ToolCallback[0])
                    .systemPrompt("回复简洁的答案")
                    .instruction("")
                    .hooks(new ProgressHook(progressEventBus))
                    .enableLogging(true)
                    .build();
            var result = agent.call("回复'ping4-ok'");
            return "OK: " + result.getText();
        } catch (Exception e) {
            log.error("ping4 failed", e);
            return "FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @GetMapping("/ping5")
    public String ping5() {
        try {
            // 只测 ModelRetryInterceptor，无 ProgressHook
            ReactAgent agent = ReactAgent.builder()
                    .name("test")
                    .model(dashscopeChatModel)
                    .tools(new org.springframework.ai.tool.ToolCallback[0])
                    .systemPrompt("回复简洁的答案")
                    .instruction("")
                    .interceptors(
                            com.alibaba.cloud.ai.graph.agent.interceptor.modelretry.ModelRetryInterceptor.builder()
                                    .maxAttempts(3).build()
                    )
                    .enableLogging(true)
                    .build();
            var result = agent.call("回复'ping5-ok'");
            return "OK: " + result.getText();
        } catch (Exception e) {
            log.error("ping5 failed", e);
            return "FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
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

    // ==================== 断点续跑 ====================

    @GetMapping(value = "/invest/analysis/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeInvestAnalysis(@RequestParam String threadId,
                                            @RequestParam(required = false) String message) {
        SseEmitter emitter = new SseEmitter(600_000L);
        StreamProgressEmitter progress = new StreamProgressEmitter(emitter);
        String requestId = UUID.randomUUID().toString();
        activeEmitters.put(requestId, emitter);
        progressEventBus.register(requestId, progress);

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        .addMetadata(ProgressHook.CONFIG_KEY, requestId)
                        .build();

                log.info("═══ 投资研报流水线断点续跑: threadId={} ═══", threadId);
                progress.progress("投资研报流水线", "从断点恢复执行: threadId=" + threadId);

                String resumeInput = (message != null && !message.isBlank()) ? message : "继续执行";
                CountDownLatch latch = new CountDownLatch(1);

                investReportPipeline.streamMessages(resumeInput, config)
                        .subscribe(
                                msg -> {},
                                error -> {
                                    log.error("断点续跑异常: {}", error.getMessage(), error);
                                    progress.agentError("投资研报流水线", error.getMessage());
                                    latch.countDown();
                                },
                                () -> {
                                    log.info("═══ 断点续跑完成 ═══");
                                    progress.complete();
                                    latch.countDown();
                                }
                        );

                latch.await();
            } catch (Exception e) {
                log.error("断点续跑异常: {}", e.getMessage(), e);
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

    private void cleanup(String requestId) {
        progressEventBus.unregister(requestId);
        activeEmitters.remove(requestId);
    }
}
