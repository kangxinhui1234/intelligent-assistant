package com.kxh.aiagent.ops.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.ProgressHook;
import com.kxh.aiagent.agent.progress.StreamProgressEmitter;
import com.kxh.aiagent.ops.budget.TokenBudgetGuard;
import com.kxh.aiagent.ops.dedup.IncidentDeduplicator;
import com.kxh.aiagent.ops.model.IncidentEvent;
import com.kxh.aiagent.ops.source.RawAlert;
import com.kxh.aiagent.ops.source.WebhookAlertSource;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * 告警处理核心服务 — Webhook / Email IMAP / MQ Consumer / 主动巡检 多入口共用。
 * <p>
 * 三种触发场景:
 *  1) {@link #process(RawAlert, StreamProgressEmitter, String)}  — 新告警全流程 (去重 + 预算 + 持久化 + 流水线)
 *  2) {@link #launchPipelineAsync}                                — 仅启动流水线 (供 MQ Consumer 调用,避免双重去重)
 *  3) {@link #resumeIncident(String, StreamProgressEmitter)}      — 已存在的 threadId 从 MysqlSaver 加载状态续跑
 */
@Service
public class IncidentProcessor {

    private static final Logger log = LoggerFactory.getLogger(IncidentProcessor.class);

    @Resource private WebhookAlertSource webhookSource;
    @Resource private IncidentDeduplicator deduplicator;
    @Resource private TokenBudgetGuard budgetGuard;
    @Resource private SequentialAgent opsIncidentPipeline;
    @Resource private ProgressEventBus progressEventBus;
    @Resource private IncidentPersister persister;
    @Resource private PersistenceWiring persistenceWiring;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.kxh.aiagent.ops.history.OpsHistoryService historyService;

    public ProcessOutcome process(RawAlert raw, StreamProgressEmitter optionalProgress, String requestId) {
        IncidentEvent incident = webhookSource.toIncident(raw);
        log.info("[处理告警] incidentId={} fingerprint={} service={} severity={} source={}",
                incident.incidentId(), incident.fingerprint(),
                incident.serviceName(), incident.severity(), raw.source());

        // 1. 去重
        if (!deduplicator.shouldProcess(incident.fingerprint())) {
            long count = deduplicator.currentCount(incident.fingerprint());
            String msg = "5min 内重复告警已合并，当前计数=" + count + "，跳过流水线";
            log.info(msg);
            if (optionalProgress != null) {
                optionalProgress.progress("Deduplicator", msg);
                optionalProgress.complete();
            }
            return ProcessOutcome.deduped(incident, count);
        }

        // 2. 预算检查
        var check = budgetGuard.canStart(incident.serviceName());
        if (!check.allowed()) {
            String msg = "[X] 预算拦截: " + check.reason() + " — " + check.message();
            log.warn(msg);
            if (optionalProgress != null) {
                optionalProgress.progress("BudgetGuard", msg);
                optionalProgress.complete();
            }
            return ProcessOutcome.budgetDenied(incident, check.reason());
        }
        budgetGuard.registerIncidentStart(incident.serviceName());
        var snap = budgetGuard.snapshot(incident.serviceName());
        if (optionalProgress != null) {
            optionalProgress.progress("BudgetGuard",
                    String.format("预算检查通过: global %d/%d, service[%s] %d/%d",
                            snap.globalIncidentsUsed(), snap.globalIncidentsMax(),
                            incident.serviceName(),
                            snap.serviceIncidentsUsed(), snap.serviceIncidentsMax()));
        }

        // 3. 持久化主记录 + 桥接 requestId/incidentId + 落 threadId
        persister.saveIncident(incident);
        persistenceWiring.track(requestId, incident.incidentId());
        String threadId = UUID.randomUUID().toString();
        persister.saveThreadId(incident.incidentId(), threadId);

        // 4. 启动流水线
        if (optionalProgress != null) {
            optionalProgress.progress("OPS事故诊断流水线",
                    "开始诊断: incidentId=" + incident.incidentId()
                            + " service=" + incident.serviceName()
                            + " threadId=" + threadId);
        }

        launchPipelineAsync(incident, threadId, requestId, optionalProgress);
        return ProcessOutcome.launched(incident, threadId);
    }

    /**
     * 异步启动流水线 — 抽出来便于 MQ Consumer / Resume 复用。
     * 内部用 CompletableFuture.runAsync 避免阻塞调用线程。
     */
    public void launchPipelineAsync(IncidentEvent incident, String threadId,
                                     String requestId, StreamProgressEmitter optionalProgress) {
        CompletableFuture.runAsync(() -> runPipelineBlocking(
                PipelineInputBuilder.build(incident), incident.incidentId(),
                threadId, requestId, optionalProgress, "launch"));
    }

    /**
     * 从已有 threadId 续跑 — MysqlSaver 会自动加载断点状态,Sequential 从未完成的 stage 继续。
     * 如果 threadId 不存在(MySQL 里 incident 没有),返回 ResumeResult.notFound。
     */
    public ResumeResult resumeIncident(String threadId, StreamProgressEmitter optionalProgress) {
        if (threadId == null || threadId.isBlank()) {
            return ResumeResult.fail("threadId 不能为空");
        }
        log.info("═══ 从断点续跑流水线: threadId={} ═══", threadId);
        String requestId = UUID.randomUUID().toString();
        // 续跑不需要从头投告警内容 — 框架按 threadId 自动加载状态。
        // 但 streamMessages 还是要传一个 input 字符串,这里用占位符。
        CompletableFuture.runAsync(() -> runPipelineBlocking(
                "继续执行 (resume threadId=" + threadId + ")",
                null, threadId, requestId, optionalProgress, "resume"));
        return ResumeResult.launched(threadId, requestId);
    }

    private void runPipelineBlocking(String input, String incidentId,
                                      String threadId, String requestId,
                                      StreamProgressEmitter optionalProgress, String mode) {
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .addMetadata(ProgressHook.CONFIG_KEY, requestId)
                    .build();
            CountDownLatch latch = new CountDownLatch(1);
            opsIncidentPipeline.streamMessages(input, config).subscribe(
                    msg -> {},
                    err -> {
                        log.error("流水线异常 mode={} threadId={}: {}", mode, threadId, err.getMessage(), err);
                        if (optionalProgress != null) {
                            optionalProgress.agentError("OPS事故诊断流水线", err.getMessage());
                        }
                        latch.countDown();
                    },
                    () -> {
                        log.info("流水线完成 mode={} threadId={}", mode, threadId);
                        if (optionalProgress != null) optionalProgress.complete();
                        latch.countDown();
                    });
            latch.await();
            if (incidentId != null) {
                persister.markResolved(incidentId, null);
                autoUpsertHistoryByIncidentId(incidentId);
            }
        } catch (Exception e) {
            log.error("流水线执行异常 mode={} threadId={}: {}", mode, threadId, e.getMessage(), e);
            if (optionalProgress != null) optionalProgress.error(e);
        } finally {
            if (requestId != null) {
                progressEventBus.unregister(requestId);
                persistenceWiring.untrack(requestId);
            }
        }
    }

    private void autoUpsertHistoryByIncidentId(String incidentId) {
        if (historyService == null) return;
        try {
            // 简化版: 用 summary 作为最小可用记录,resolution 留空
            // 真实 resolution 由 OpsHistoryController.migrate 端点批量回填更稳妥
            // resume 场景下也走这条 — 续跑完成同样要刷新历史 RAG
            com.kxh.aiagent.ops.entity.OpsIncident row = persister.findIncidentById(incidentId);
            if (row == null) return;
            com.kxh.aiagent.ops.history.OpsHistoryRecord record =
                    new com.kxh.aiagent.ops.history.OpsHistoryRecord(
                            row.getId(),
                            row.getServiceName(),
                            null,
                            row.getSeverity(),
                            row.getOccurredAt() == null ? 0
                                    : row.getOccurredAt().atZone(java.time.ZoneId.systemDefault())
                                            .toEpochSecond(),
                            row.getSummary(),
                            ""
                    );
            historyService.upsert(record);
        } catch (Exception e) {
            log.warn("autoUpsertHistory 失败 incidentId={}: {}", incidentId, e.getMessage());
        }
    }

    public record ProcessOutcome(
            Status status,
            IncidentEvent incident,
            String detail
    ) {
        public enum Status { LAUNCHED, DEDUPED, BUDGET_DENIED }
        public static ProcessOutcome launched(IncidentEvent ev, String threadId) {
            return new ProcessOutcome(Status.LAUNCHED, ev, "threadId=" + threadId);
        }
        public static ProcessOutcome deduped(IncidentEvent ev, long count) {
            return new ProcessOutcome(Status.DEDUPED, ev, "dedupCount=" + count);
        }
        public static ProcessOutcome budgetDenied(IncidentEvent ev, String reason) {
            return new ProcessOutcome(Status.BUDGET_DENIED, ev, reason);
        }
    }

    public record ResumeResult(boolean ok, String threadId, String requestId, String message) {
        public static ResumeResult launched(String tid, String rid) {
            return new ResumeResult(true, tid, rid, "resumed");
        }
        public static ResumeResult fail(String msg) {
            return new ResumeResult(false, null, null, msg);
        }
    }
}
