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
 * 告警处理核心服务 — Webhook / Email IMAP / 主动巡检 三种入口共用此服务。
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

        // 3. 持久化主记录 + 桥接 requestId/incidentId
        persister.saveIncident(incident);
        persistenceWiring.track(requestId, incident.incidentId());

        // 4. 启动流水线
        String threadId = UUID.randomUUID().toString();
        if (optionalProgress != null) {
            optionalProgress.progress("OPS事故诊断流水线",
                    "开始诊断: incidentId=" + incident.incidentId()
                            + " service=" + incident.serviceName()
                            + " threadId=" + threadId);
        }

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        .addMetadata(ProgressHook.CONFIG_KEY, requestId)
                        .build();
                String input = PipelineInputBuilder.build(incident);
                CountDownLatch latch = new CountDownLatch(1);
                opsIncidentPipeline.streamMessages(input, config).subscribe(
                        msg -> {},
                        err -> {
                            log.error("流水线异常 incidentId={}: {}", incident.incidentId(), err.getMessage(), err);
                            if (optionalProgress != null) {
                                optionalProgress.agentError("OPS事故诊断流水线", err.getMessage());
                            }
                            latch.countDown();
                        },
                        () -> {
                            log.info("流水线完成 incidentId={}", incident.incidentId());
                            if (optionalProgress != null) optionalProgress.complete();
                            latch.countDown();
                        });
                latch.await();
                persister.markResolved(incident.incidentId(), null);
                // 流水线完成 → 自动入历史 RAG 库
                autoUpsertHistory(incident);
            } catch (Exception e) {
                log.error("流水线执行异常 incidentId={}: {}", incident.incidentId(), e.getMessage(), e);
                if (optionalProgress != null) optionalProgress.error(e);
            } finally {
                progressEventBus.unregister(requestId);
                persistenceWiring.untrack(requestId);
            }
        });

        return ProcessOutcome.launched(incident, threadId);
    }

    /**
     * 流水线完成后,把 RootCauseAgent 的输出当作 resolution 写入 Milvus 历史库。
     * 注意: 同一 incidentId upsert,所以重跑也不会重复。
     */
    private void autoUpsertHistory(IncidentEvent incident) {
        if (historyService == null) return;
        try {
            // 从 PersistenceWiring/MySQL 异步反查可能延迟,这里采用最简单粗暴方式:
            // 通过 IncidentPersister 读 MySQL 取 RootCauseAgent 的最新输出
            // 由 PersistenceWiring 已经写入,这里再异步触发一次 upsert
            // 简化版: 用 summary 作为最小可用记录,resolution 留空
            // 真实 resolution 由 OpsHistoryController.migrate 端点批量回填更稳妥
            com.kxh.aiagent.ops.history.OpsHistoryRecord record =
                    new com.kxh.aiagent.ops.history.OpsHistoryRecord(
                            incident.incidentId(),
                            incident.serviceName(),
                            null,
                            incident.severity() == null ? null : incident.severity().name(),
                            incident.occurredAt() == null ? 0 : incident.occurredAt().getEpochSecond(),
                            incident.summary(),
                            "" // resolution 留空,后续由 migrate 端点完整回填
                    );
            historyService.upsert(record);
        } catch (Exception e) {
            log.warn("autoUpsertHistory 失败 incidentId={}: {}", incident.incidentId(), e.getMessage());
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
}
