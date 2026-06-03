package com.kxh.aiagent.ops.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.entity.OpsInvestigationLog;
import com.kxh.aiagent.ops.mapper.OpsActionLogMapper;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import com.kxh.aiagent.ops.mapper.OpsInvestigationLogMapper;
import com.kxh.aiagent.ops.model.IncidentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 事故持久化服务。
 * 流水线各阶段写入 MySQL,所有写操作都做防御性 try-catch,失败不阻塞主流程。
 */
@Service
public class IncidentPersister {

    private static final Logger log = LoggerFactory.getLogger(IncidentPersister.class);

    @Autowired(required = false) private OpsIncidentMapper incidentMapper;
    @Autowired(required = false) private OpsInvestigationLogMapper investigationMapper;
    @Autowired(required = false) private OpsActionLogMapper actionMapper;

    public void saveIncident(IncidentEvent event) {
        if (incidentMapper == null) return;
        try {
            OpsIncident e = new OpsIncident();
            e.setId(event.incidentId());
            e.setFingerprint(event.fingerprint());
            e.setSource(event.source());
            e.setServiceName(event.serviceName());
            e.setSeverity(event.severity() == null ? null : event.severity().name());
            e.setStatus("investigating");
            e.setOccurredAt(toLocal(event.occurredAt() == null ? null : event.occurredAt().toEpochMilli()));
            e.setReceivedAt(LocalDateTime.now());
            e.setSummary(event.summary());
            incidentMapper.insert(e);
        } catch (Exception ex) {
            log.warn("saveIncident 失败 incidentId={}: {}", event.incidentId(), ex.getMessage());
        }
    }

    public void saveInvestigationLog(String incidentId, String agentName, String outputText,
                                      Long durationMs, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        if (investigationMapper == null || incidentId == null || agentName == null) return;
        try {
            OpsInvestigationLog l = new OpsInvestigationLog();
            l.setIncidentId(incidentId);
            l.setAgentName(agentName);
            l.setOutputText(truncateOutput(outputText));
            l.setDurationMs(durationMs);
            l.setPromptTokens(promptTokens);
            l.setCompletionTokens(completionTokens);
            l.setTotalTokens(totalTokens);
            investigationMapper.insert(l);
        } catch (Exception ex) {
            log.warn("saveInvestigationLog 失败 incidentId={} agent={}: {}", incidentId, agentName, ex.getMessage());
        }
    }

    public void saveAction(String incidentId, String actionType, String target,
                            String triggeredBy, String resultStatus, String resultMessage) {
        if (actionMapper == null) return;
        try {
            OpsActionLog a = new OpsActionLog();
            a.setIncidentId(incidentId);
            a.setActionType(actionType);
            a.setTarget(target);
            a.setTriggeredBy(triggeredBy);
            a.setTriggeredAt(LocalDateTime.now());
            a.setExecutedAt(LocalDateTime.now());
            a.setResultStatus(resultStatus);
            a.setResultMessage(truncateOutput(resultMessage));
            actionMapper.insert(a);
        } catch (Exception ex) {
            log.warn("saveAction 失败 incidentId={}: {}", incidentId, ex.getMessage());
        }
    }

    public void markResolved(String incidentId, String reportPath) {
        if (incidentMapper == null) return;
        try {
            UpdateWrapper<OpsIncident> w = new UpdateWrapper<>();
            w.eq("id", incidentId)
                    .set("status", "resolved")
                    .set("resolved_at", LocalDateTime.now())
                    .set("report_path", reportPath);
            incidentMapper.update(null, w);
        } catch (Exception ex) {
            log.warn("markResolved 失败 incidentId={}: {}", incidentId, ex.getMessage());
        }
    }

    private LocalDateTime toLocal(Long epochMs) {
        if (epochMs == null) return null;
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    private String truncateOutput(String s) {
        if (s == null) return null;
        if (s.length() > 60000) return s.substring(0, 60000) + "...[truncated]";
        return s;
    }
}
