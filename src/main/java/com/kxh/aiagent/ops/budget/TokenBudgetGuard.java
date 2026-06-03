package com.kxh.aiagent.ops.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 预算守门员 — 防止 LLM 调用失控。
 * <p>
 * 由于当前框架未把 token 用量写到 OverAllState,token 计数不可靠,
 * 所以以"每日 incident 事件数"作为主要硬上限(确定能数得清);
 * token 累计仅用于信息展示。
 */
@Component
public class TokenBudgetGuard {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetGuard.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(7);

    /** 单次 incident 内 token 累计警戒值 (informational only) */
    @Value("${ops.budget.incident.max-tokens:25000}")
    private long incidentMaxTokens;

    /** 每服务每日 incident 上限 — 主硬控 */
    @Value("${ops.budget.service-daily.max-incidents:100}")
    private int serviceDailyMaxIncidents;

    /** 全平台每日 incident 上限 — 主硬控 */
    @Value("${ops.budget.global-daily.max-incidents:1000}")
    private int globalDailyMaxIncidents;

    @Value("${ops.budget.enabled:true}")
    private boolean enabled;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    /**
     * 流水线启动前调用,判断是否允许新开 incident。
     * 注意: 通过返回 BudgetCheckResult 不会增加计数,只有 registerIncidentStart 才增加。
     */
    public BudgetCheckResult canStart(String serviceName) {
        if (!enabled || redis == null) return BudgetCheckResult.permit();

        long globalToday = readLong("ops:budget:incidents:global:" + today());
        if (globalToday >= globalDailyMaxIncidents) {
            String msg = "全平台每日 incident 配额已用尽: " + globalToday + "/" + globalDailyMaxIncidents;
            log.warn("预算拦截: {}", msg);
            return BudgetCheckResult.deny("global_daily_exceeded", msg);
        }

        String svc = serviceName == null ? "unknown" : serviceName;
        long svcToday = readLong("ops:budget:incidents:service:" + svc + ":" + today());
        if (svcToday >= serviceDailyMaxIncidents) {
            String msg = "服务 " + svc + " 每日 incident 配额已用尽: " + svcToday + "/" + serviceDailyMaxIncidents;
            log.warn("预算拦截: {}", msg);
            return BudgetCheckResult.deny("service_daily_exceeded", msg);
        }

        return BudgetCheckResult.permit();
    }

    /** 流水线确认启动时调用,增加计数 */
    public void registerIncidentStart(String serviceName) {
        if (!enabled || redis == null) return;
        String d = today();
        String svc = serviceName == null ? "unknown" : serviceName;
        String globalKey = "ops:budget:incidents:global:" + d;
        String svcKey = "ops:budget:incidents:service:" + svc + ":" + d;
        redis.opsForValue().increment(globalKey);
        redis.expire(globalKey, TTL);
        redis.opsForValue().increment(svcKey);
        redis.expire(svcKey, TTL);

        long globalUsed = readLong(globalKey);
        if (globalUsed == (long)(globalDailyMaxIncidents * 0.8)) {
            log.warn("⚠ 全平台每日 incident 已用 80%: {}/{}", globalUsed, globalDailyMaxIncidents);
        }
    }

    /** Agent 完成后累计 token (信息性,目前框架返回的多为 0/null) */
    public void recordTokens(String serviceName, int tokens) {
        if (!enabled || redis == null || tokens <= 0) return;
        String d = today();
        String svc = serviceName == null ? "unknown" : serviceName;
        String globalKey = "ops:budget:tokens:global:" + d;
        String svcKey = "ops:budget:tokens:service:" + svc + ":" + d;
        redis.opsForValue().increment(globalKey, tokens);
        redis.expire(globalKey, TTL);
        redis.opsForValue().increment(svcKey, tokens);
        redis.expire(svcKey, TTL);
    }

    public BudgetSnapshot snapshot(String serviceName) {
        if (!enabled || redis == null) {
            return new BudgetSnapshot(0, globalDailyMaxIncidents, 0, serviceDailyMaxIncidents, 0, 0);
        }
        long gi = readLong("ops:budget:incidents:global:" + today());
        String svc = serviceName == null ? "unknown" : serviceName;
        long si = readLong("ops:budget:incidents:service:" + svc + ":" + today());
        long gt = readLong("ops:budget:tokens:global:" + today());
        long st = readLong("ops:budget:tokens:service:" + svc + ":" + today());
        return new BudgetSnapshot(gi, globalDailyMaxIncidents, si, serviceDailyMaxIncidents, gt, st);
    }

    private long readLong(String key) {
        String v = redis.opsForValue().get(key);
        if (v == null) return 0;
        try { return Long.parseLong(v); } catch (Exception e) { return 0; }
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    public record BudgetCheckResult(boolean allowed, String reason, String message) {
        public static BudgetCheckResult permit() { return new BudgetCheckResult(true, null, null); }
        public static BudgetCheckResult deny(String reason, String message) {
            return new BudgetCheckResult(false, reason, message);
        }
    }

    public record BudgetSnapshot(
            long globalIncidentsUsed, long globalIncidentsMax,
            long serviceIncidentsUsed, long serviceIncidentsMax,
            long globalTokensUsed, long serviceTokensUsed
    ) {}
}
