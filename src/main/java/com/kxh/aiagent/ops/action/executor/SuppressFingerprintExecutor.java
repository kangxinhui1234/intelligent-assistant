package com.kxh.aiagent.ops.action.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.ops.action.ActionExecutor;
import com.kxh.aiagent.ops.action.ActionType;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 把指定 fingerprint 写入 Redis 屏蔽集合,N 小时内 IncidentDeduplicator 直接丢弃同类告警。
 * Redis key: ops:suppress:{fingerprint}, value = 屏蔽原因, TTL = hours * 3600s
 */
@Component
public class SuppressFingerprintExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(SuppressFingerprintExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String SUPPRESS_KEY_PREFIX = "ops:suppress:";

    @Autowired(required = false)
    private StringRedisTemplate redis;

    @Resource(name = "opsIncidentMapper")
    private OpsIncidentMapper incidentMapper;

    @Value("${ops.suppression.default-hours:2}")
    private int defaultHours;

    @Value("${ops.suppression.max-hours:24}")
    private int maxHours;

    @Override public ActionType type() { return ActionType.SUPPRESS_FINGERPRINT; }

    @Override
    public ExecutionResult execute(OpsActionLog logRow) {
        if (redis == null) {
            return ExecutionResult.failed("Redis 未配置, 无法屏蔽");
        }
        try {
            // 反查 incident 拿 fingerprint
            OpsIncident incident = incidentMapper.selectById(logRow.getIncidentId());
            if (incident == null || incident.getFingerprint() == null) {
                return ExecutionResult.failed("找不到 incident 或 fingerprint 为空 incidentId=" + logRow.getIncidentId());
            }
            int hours = parseHours(logRow.getProposalPayload());
            String reason = "由动作 #" + logRow.getId() + " 屏蔽: " + safeRationale(logRow.getRationale());
            String key = SUPPRESS_KEY_PREFIX + incident.getFingerprint();

            redis.opsForValue().set(key, reason, Duration.ofHours(hours));
            log.info("[SuppressFingerprint] fp={} hours={}", incident.getFingerprint(), hours);
            return ExecutionResult.ok("已屏蔽 fingerprint=" + incident.getFingerprint() + " " + hours + " 小时");
        } catch (Exception e) {
            log.error("SuppressFingerprint 失败: {}", e.getMessage(), e);
            return ExecutionResult.failed(e.getMessage());
        }
    }

    private int parseHours(String payload) {
        int h = defaultHours;
        if (payload != null && !payload.isBlank()) {
            try {
                JsonNode n = mapper.readTree(payload);
                if (n.hasNonNull("hours")) h = n.get("hours").asInt(defaultHours);
            } catch (Exception ignored) { }
        }
        if (h <= 0) h = defaultHours;
        if (h > maxHours) h = maxHours;
        return h;
    }

    private String safeRationale(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
