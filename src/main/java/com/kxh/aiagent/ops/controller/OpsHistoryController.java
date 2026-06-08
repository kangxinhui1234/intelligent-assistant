package com.kxh.aiagent.ops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.entity.OpsInvestigationLog;
import com.kxh.aiagent.ops.history.OpsHistoryRecord;
import com.kxh.aiagent.ops.history.OpsHistoryService;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import com.kxh.aiagent.ops.mapper.OpsInvestigationLogMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ops/history")
public class OpsHistoryController {

    private static final Logger log = LoggerFactory.getLogger(OpsHistoryController.class);

    @Resource(name = "opsIncidentMapper") private OpsIncidentMapper incidentMapper;
    @Resource(name = "opsInvestigationLogMapper") private OpsInvestigationLogMapper investigationMapper;
    @Autowired(required = false) private OpsHistoryService historyService;

    /**
     * 把 MySQL 中有 RootCauseAgent 输出的 incident 批量入 Milvus。
     * POST /api/ops/history/migrate?limit=100&onlyResolved=false
     * <p>
     * 默认放宽: 只要有 RootCauseAgent 输出就入库,无视 status。
     */
    @RequestMapping(value = "/migrate", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> migrate(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean onlyResolved) {
        Map<String, Object> resp = new HashMap<>();
        if (historyService == null) {
            resp.put("status", "history_not_enabled");
            return resp;
        }
        QueryWrapper<OpsIncident> q = new QueryWrapper<>();
        if (onlyResolved) q.eq("status", "resolved");
        q.orderByDesc("created_at").last("LIMIT " + Math.min(limit, 500));
        List<OpsIncident> incidents = incidentMapper.selectList(q);

        int success = 0;
        int skipped = 0;
        for (OpsIncident inc : incidents) {
            // 取该 incident 的 RootCauseAgent 输出作为 resolution
            OpsInvestigationLog rc = investigationMapper.selectOne(
                    new QueryWrapper<OpsInvestigationLog>()
                            .eq("incident_id", inc.getId())
                            .eq("agent_name", "RootCauseAgent")
                            .last("LIMIT 1"));
            if (rc == null || rc.getOutputText() == null || rc.getOutputText().isBlank()) {
                skipped++;
                continue;
            }
            OpsHistoryRecord record = new OpsHistoryRecord(
                    inc.getId(),
                    inc.getServiceName(),
                    extractErrorClass(inc.getSummary()),
                    inc.getSeverity(),
                    inc.getOccurredAt() == null ? 0 : inc.getOccurredAt().toEpochSecond(ZoneOffset.UTC),
                    inc.getSummary(),
                    rc.getOutputText()
            );
            historyService.upsert(record);
            success++;
        }
        log.info("[migrate] 总数={} 成功={} 跳过={}", incidents.size(), success, skipped);
        resp.put("status", "ok");
        resp.put("scanned", incidents.size());
        resp.put("upserted", success);
        resp.put("skipped", skipped);
        return resp;
    }

    /** 测试用: 直接对历史库执行混合检索 */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String query,
                                       @RequestParam(required = false) String service,
                                       @RequestParam(required = false) String errorClass) {
        Map<String, Object> resp = new HashMap<>();
        if (historyService == null) {
            resp.put("status", "history_not_enabled");
            return resp;
        }
        var hits = historyService.hybridSearch(query, service, errorClass);
        resp.put("status", "ok");
        resp.put("count", hits.size());
        resp.put("results", hits);
        return resp;
    }

    private String extractErrorClass(String summary) {
        if (summary == null) return null;
        var m = java.util.regex.Pattern.compile("([\\w$.]+(?:Exception|Error))").matcher(summary);
        if (m.find()) return m.group(1);
        return null;
    }
}
