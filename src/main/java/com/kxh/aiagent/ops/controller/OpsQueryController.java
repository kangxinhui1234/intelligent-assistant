package com.kxh.aiagent.ops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.entity.OpsInvestigationLog;
import com.kxh.aiagent.ops.mapper.OpsActionLogMapper;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import com.kxh.aiagent.ops.mapper.OpsInvestigationLogMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ops/incidents")
public class OpsQueryController {

    @Resource(name = "opsIncidentMapper") private OpsIncidentMapper incidentMapper;
    @Resource(name = "opsInvestigationLogMapper") private OpsInvestigationLogMapper investigationMapper;
    @Resource(name = "opsActionLogMapper") private OpsActionLogMapper actionMapper;

    /**
     * 分页查询事故列表
     * GET /api/ops/incidents?service=fengine-admin&days=7&page=1&size=20
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        QueryWrapper<OpsIncident> q = new QueryWrapper<>();
        if (service != null && !service.isBlank()) {
            q.eq("service_name", service);
        }
        if (status != null && !status.isBlank()) {
            q.eq("status", status);
        }
        if (days > 0) {
            q.ge("created_at", LocalDateTime.now().minusDays(days));
        }
        q.orderByDesc("created_at");

        Page<OpsIncident> pageObj = new Page<>(page, Math.min(size, 100));
        Page<OpsIncident> result = incidentMapper.selectPage(pageObj, q);

        Map<String, Object> resp = new HashMap<>();
        resp.put("total", result.getTotal());
        resp.put("page", result.getCurrent());
        resp.put("size", result.getSize());
        resp.put("records", result.getRecords());
        return resp;
    }

    /**
     * 事故详情(主信息 + 所有调查日志 + 所有动作)
     * GET /api/ops/incidents/{id}
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable("id") String id) {
        OpsIncident incident = incidentMapper.selectById(id);
        if (incident == null) {
            return Map.of("error", "not_found", "id", id);
        }
        List<OpsInvestigationLog> investigations = investigationMapper.selectList(
                new QueryWrapper<OpsInvestigationLog>()
                        .eq("incident_id", id)
                        .orderByAsc("id"));
        List<OpsActionLog> actions = actionMapper.selectList(
                new QueryWrapper<OpsActionLog>()
                        .eq("incident_id", id)
                        .orderByAsc("id"));

        Map<String, Object> resp = new HashMap<>();
        resp.put("incident", incident);
        resp.put("investigations", investigations);
        resp.put("actions", actions);
        return resp;
    }

    /**
     * 服务维度统计 — 最近 N 天每个服务的告警数
     * GET /api/ops/incidents/stats/by-service?days=7
     */
    @GetMapping("/stats/by-service")
    public Map<String, Object> statsByService(@RequestParam(defaultValue = "7") int days) {
        QueryWrapper<OpsIncident> q = new QueryWrapper<>();
        q.select("service_name", "count(*) as count")
                .ge("created_at", LocalDateTime.now().minusDays(days))
                .groupBy("service_name")
                .orderByDesc("count");
        List<Map<String, Object>> rows = incidentMapper.selectMaps(q);
        Map<String, Object> resp = new HashMap<>();
        resp.put("days", days);
        resp.put("data", rows);
        return resp;
    }
}
