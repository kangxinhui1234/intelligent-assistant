package com.kxh.aiagent.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动时自动续跑 — 扫表找 status=investigating 且有 thread_id 的事故,
 * 调 IncidentProcessor.resumeIncident,框架会从 MysqlSaver 加载断点状态从未完成的 stage 继续。
 * <p>
 * 触发条件 (任一不满足都跳过):
 *  - status='investigating' 且 thread_id 非空
 *  - 距 received_at 不超过 ops.checkpoint.resume-window-hours (默认 24h,避免太老的事故复活)
 * <p>
 * 配置: ops.checkpoint.auto-resume-on-startup (默认 true)
 */
@Component
public class ResumeOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResumeOnStartup.class);

    @Resource(name = "opsIncidentMapper")
    private OpsIncidentMapper incidentMapper;

    @Resource
    private IncidentProcessor processor;

    @Value("${ops.checkpoint.auto-resume-on-startup:true}")
    private boolean enabled;

    @Value("${ops.checkpoint.resume-window-hours:24}")
    private int resumeWindowHours;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[ResumeOnStartup] 已禁用 (ops.checkpoint.auto-resume-on-startup=false)");
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofHours(resumeWindowHours));
            QueryWrapper<OpsIncident> q = new QueryWrapper<>();
            q.eq("status", "investigating")
                    .isNotNull("thread_id")
                    .ge("received_at", cutoff)
                    .orderByAsc("created_at");
            List<OpsIncident> rows = incidentMapper.selectList(q);

            if (rows.isEmpty()) {
                log.info("[ResumeOnStartup] 无未完成的事故需要续跑");
                return;
            }
            log.warn("[ResumeOnStartup] 发现 {} 个未完成事故 (status=investigating), 开始自动续跑...", rows.size());
            for (OpsIncident row : rows) {
                try {
                    IncidentProcessor.ResumeResult r =
                            processor.resumeIncident(row.getThreadId(), null);
                    log.info("[ResumeOnStartup] incidentId={} threadId={} → ok={} msg={}",
                            row.getId(), row.getThreadId(), r.ok(), r.message());
                } catch (Exception e) {
                    log.error("[ResumeOnStartup] 续跑失败 incidentId={}: {}", row.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("[ResumeOnStartup] 扫表续跑整体异常: {}", e.getMessage(), e);
        }
    }
}
