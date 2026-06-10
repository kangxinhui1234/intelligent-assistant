package com.kxh.aiagent.ops.action.executor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.ops.action.ActionExecutor;
import com.kxh.aiagent.ops.action.ActionType;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.entity.OpsInvestigationLog;
import com.kxh.aiagent.ops.history.OpsHistoryRecord;
import com.kxh.aiagent.ops.history.OpsHistoryService;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import com.kxh.aiagent.ops.mapper.OpsInvestigationLogMapper;
import com.kxh.aiagent.ops.service.IncidentPersister;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标记事故为"已知问题",把 (summary + 人工补充的 resolution) 写入 Milvus 历史 RAG,
 * 后续类似告警可通过混合检索直接命中。
 * <p>
 * Proposal payload: { "errorClass": "java.lang.NullPointerException", "resolution": "..." }
 * resolution 是必填的(否则没必要入库)。
 */
@Component
public class MarkAsKnownExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(MarkAsKnownExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Resource(name = "opsIncidentMapper")
    private OpsIncidentMapper incidentMapper;

    @Resource(name = "opsInvestigationLogMapper")
    private OpsInvestigationLogMapper investigationMapper;

    @Resource
    private IncidentPersister persister;

    @Autowired(required = false)
    private OpsHistoryService historyService;

    @Override public ActionType type() { return ActionType.MARK_AS_KNOWN; }

    @Override
    public ExecutionResult execute(OpsActionLog logRow) {
        try {
            OpsIncident incident = incidentMapper.selectById(logRow.getIncidentId());
            if (incident == null) {
                return ExecutionResult.failed("找不到 incident " + logRow.getIncidentId());
            }

            String resolution = extractResolution(logRow.getProposalPayload());
            String errorClass = extractErrorClass(logRow.getProposalPayload());
            if (resolution == null || resolution.isBlank()) {
                resolution = buildFallbackResolution(logRow.getIncidentId());
            }

            // 1) 写 Milvus 历史 RAG
            String milvusResult;
            if (historyService != null) {
                OpsHistoryRecord record = new OpsHistoryRecord(
                        incident.getId(),
                        incident.getServiceName(),
                        errorClass,
                        incident.getSeverity(),
                        incident.getOccurredAt() == null
                                ? System.currentTimeMillis() / 1000
                                : incident.getOccurredAt().atZone(java.time.ZoneId.systemDefault())
                                        .toEpochSecond(),
                        incident.getSummary(),
                        resolution
                );
                historyService.upsert(record);
                milvusResult = "Milvus upsert OK";
            } else {
                milvusResult = "Milvus 未启用,仅更新 MySQL";
            }

            // 2) 更新事故状态
            persister.markResolved(logRow.getIncidentId(), null);

            log.info("[MarkAsKnown] incidentId={} resolution len={}",
                    logRow.getIncidentId(), resolution.length());
            return ExecutionResult.ok(milvusResult + "; 已标记 resolved");
        } catch (Exception e) {
            log.error("MarkAsKnown 失败: {}", e.getMessage(), e);
            return ExecutionResult.failed(e.getMessage());
        }
    }

    private String extractResolution(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode n = mapper.readTree(payload);
            if (n.hasNonNull("resolution")) return n.get("resolution").asText();
        } catch (Exception ignored) { }
        return null;
    }

    private String extractErrorClass(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode n = mapper.readTree(payload);
            if (n.hasNonNull("errorClass")) return n.get("errorClass").asText();
        } catch (Exception ignored) { }
        return null;
    }

    private String buildFallbackResolution(String incidentId) {
        // 没人工填,就把 RootCauseAgent 的输出当 resolution 兜底
        List<OpsInvestigationLog> rows = investigationMapper.selectList(
                new QueryWrapper<OpsInvestigationLog>()
                        .eq("incident_id", incidentId)
                        .eq("agent_name", "RootCauseAgent")
                        .orderByDesc("id"));
        if (rows.isEmpty() || rows.get(0).getOutputText() == null) {
            return "(未提供 resolution,审批人也未补充)";
        }
        String s = rows.get(0).getOutputText();
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
