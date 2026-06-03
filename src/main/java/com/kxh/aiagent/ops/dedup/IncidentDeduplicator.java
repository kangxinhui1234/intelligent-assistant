package com.kxh.aiagent.ops.dedup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IncidentDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(IncidentDeduplicator.class);
    private static final String KEY_PREFIX = "ops:dedup:";
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Autowired(required = false)
    private StringRedisTemplate redis;

    public boolean shouldProcess(String fingerprint) {
        if (redis == null) {
            log.debug("Redis 未配置，跳过去重");
            return true;
        }
        String key = KEY_PREFIX + fingerprint;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, WINDOW);
        boolean first = count != null && count == 1L;
        if (!first) {
            log.info("Incident 已去重: fingerprint={} count={}", fingerprint, count);
        }
        return first;
    }

    public long currentCount(String fingerprint) {
        if (redis == null) return 0L;
        String v = redis.opsForValue().get(KEY_PREFIX + fingerprint);
        return v == null ? 0L : Long.parseLong(v);
    }
}
