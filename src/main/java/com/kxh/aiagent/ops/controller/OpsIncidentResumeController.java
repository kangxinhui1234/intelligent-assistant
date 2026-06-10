package com.kxh.aiagent.ops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.StreamProgressEmitter;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import com.kxh.aiagent.ops.service.IncidentProcessor;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 事故流水线断点续跑端点。
 * <p>
 * 与普通 SSE 进度推送的区别:这里直接走 MysqlSaver 加载断点状态,
 * 流水线从未完成的 stage 继续,无需重新发告警邮件。
 */
@RestController
@RequestMapping("/ops/incidents")
public class OpsIncidentResumeController {

    private static final Logger log = LoggerFactory.getLogger(OpsIncidentResumeController.class);

    @Resource(name = "opsIncidentMapper")
    private OpsIncidentMapper incidentMapper;

    @Resource
    private IncidentProcessor processor;

    @Resource
    private ProgressEventBus progressEventBus;

    /**
     * SSE 续跑 — 浏览器调用,可实时看进度。
     */
    @GetMapping(value = "/{id}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeWithStream(@PathVariable("id") String id) {
        OpsIncident row = incidentMapper.selectById(id);
        if (row == null || row.getThreadId() == null) {
            SseEmitter emitter = new SseEmitter(5_000L);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("incident " + id + " 不存在或缺 threadId"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(600_000L);
        StreamProgressEmitter progress = new StreamProgressEmitter(emitter);
        String requestId = UUID.randomUUID().toString();
        progressEventBus.register(requestId, progress);

        IncidentProcessor.ResumeResult r = processor.resumeIncident(row.getThreadId(), progress);
        log.info("[Resume] incidentId={} threadId={} requestId={}", id, row.getThreadId(), requestId);
        if (!r.ok()) {
            try { emitter.send(SseEmitter.event().name("error").data(r.message())); }
            catch (Exception ignored) {}
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 同步触发续跑 — 给管理页 / 脚本调用,只返回是否启动成功,进度看日志。
     */
    @PostMapping("/{id}/resume")
    public Map<String, Object> resumeFire(@PathVariable("id") String id) {
        OpsIncident row = incidentMapper.selectById(id);
        Map<String, Object> resp = new HashMap<>();
        if (row == null) {
            resp.put("ok", false);
            resp.put("message", "not_found");
            return resp;
        }
        if (row.getThreadId() == null || row.getThreadId().isBlank()) {
            resp.put("ok", false);
            resp.put("message", "no_thread_id");
            return resp;
        }
        IncidentProcessor.ResumeResult r = processor.resumeIncident(row.getThreadId(), null);
        resp.put("ok", r.ok());
        resp.put("threadId", row.getThreadId());
        resp.put("message", r.message());
        return resp;
    }

    /**
     * 列出可续跑的事故 (status=investigating 且有 thread_id)。
     */
    @GetMapping("/resumable")
    public List<OpsIncident> resumable() {
        QueryWrapper<OpsIncident> q = new QueryWrapper<>();
        q.eq("status", "investigating")
                .isNotNull("thread_id")
                .orderByDesc("created_at");
        return incidentMapper.selectList(q);
    }
}
