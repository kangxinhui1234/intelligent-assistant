package com.kxh.aiagent.ops.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮件四道限流闸门 — 用 Redis 滑动窗口实现。
 * 任一闸门触发都立即停止本轮处理。
 */
@Component
public class EmailRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(EmailRateLimiter.class);
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${ops.source.email.max-per-poll:1}")
    private int maxPerPoll;

    @Value("${ops.source.email.max-per-hour:5}")
    private int maxPerHour;

    @Value("${ops.source.email.max-per-day:30}")
    private int maxPerDay;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    /**
     * @return 本轮还能处理多少封 (受 per-poll / hour / day 综合限制)
     */
    public int remainingQuota() {
        if (redis == null) return maxPerPoll;
        long hourUsed = readLong("ops:email:rate:hour:" + hourKey());
        long dayUsed = readLong("ops:email:rate:day:" + dayKey());
        long hourRemain = Math.max(0, maxPerHour - hourUsed);
        long dayRemain = Math.max(0, maxPerDay - dayUsed);
        long min = Math.min(maxPerPoll, Math.min(hourRemain, dayRemain));
        log.debug("Email quota: perPoll={} hour={}/{} day={}/{} → remain={}",
                maxPerPoll, hourUsed, maxPerHour, dayUsed, maxPerDay, min);
        return (int) min;
    }

    /** 处理一封后调用,增加 hour/day 计数 */
    public void recordOne() {
        if (redis == null) return;
        String hk = "ops:email:rate:hour:" + hourKey();
        String dk = "ops:email:rate:day:" + dayKey();
        redis.opsForValue().increment(hk);
        redis.expire(hk, Duration.ofHours(2));
        redis.opsForValue().increment(dk);
        redis.expire(dk, Duration.ofDays(2));
    }

    public RateSnapshot snapshot() {
        if (redis == null) return new RateSnapshot(0, maxPerHour, 0, maxPerDay, maxPerPoll);
        return new RateSnapshot(
                readLong("ops:email:rate:hour:" + hourKey()), maxPerHour,
                readLong("ops:email:rate:day:" + dayKey()), maxPerDay,
                maxPerPoll
        );
    }

    private long readLong(String key) {
        String v = redis.opsForValue().get(key);
        if (v == null) return 0;
        try { return Long.parseLong(v); } catch (Exception e) { return 0; }
    }

    private String hourKey() { return LocalDateTime.now().format(HOUR_FMT); }
    private String dayKey() { return LocalDate.now().format(DAY_FMT); }

    public record RateSnapshot(
            long hourUsed, long hourMax,
            long dayUsed, long dayMax,
            long perPollMax
    ) {}
}
