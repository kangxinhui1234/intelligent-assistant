package com.kxh.aiagent.ops.action.executor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.ops.action.ActionExecutor;
import com.kxh.aiagent.ops.action.ActionType;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.entity.OpsInvestigationLog;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import com.kxh.aiagent.ops.mapper.OpsInvestigationLogMapper;
import com.kxh.aiagent.ops.tool.EmailNotifyTool;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 把完整诊断报告(各 Agent 的输出汇总)邮件发给指定负责人列表。
 * Proposal payload: { "recipients": ["a@x.com", "b@x.com"] }
 */
@Component
public class NotifyOwnerExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(NotifyOwnerExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Resource(name = "opsIncidentMapper")
    private OpsIncidentMapper incidentMapper;

    @Resource(name = "opsInvestigationLogMapper")
    private OpsInvestigationLogMapper investigationMapper;

    @Resource
    private EmailNotifyTool emailTool;

    @Override public ActionType type() { return ActionType.NOTIFY_OWNER; }

    @Override
    public ExecutionResult execute(OpsActionLog logRow) {
        try {
            List<String> recipients = parseRecipients(logRow.getProposalPayload());
            if (recipients.isEmpty()) {
                recipients = emailTool.defaultReviewers();
            }
            if (recipients.isEmpty()) {
                return ExecutionResult.failed("recipients 未指定且 reviewers 默认未配置");
            }

            OpsIncident incident = incidentMapper.selectById(logRow.getIncidentId());
            if (incident == null) {
                return ExecutionResult.failed("找不到 incident " + logRow.getIncidentId());
            }

            List<OpsInvestigationLog> investigations = investigationMapper.selectList(
                    new QueryWrapper<OpsInvestigationLog>()
                            .eq("incident_id", logRow.getIncidentId())
                            .orderByAsc("id"));

            String subject = "[AIOps 升级通知] " + safe(incident.getServiceName(), "unknown")
                    + " - " + safe(incident.getSeverity(), "P1");
            String html = buildHtml(incident, investigations, logRow.getRationale());

            String result = emailTool.sendHtml(recipients, subject, html);
            if (result.startsWith("failed:")) {
                return ExecutionResult.failed(result);
            }
            return ExecutionResult.ok("已通知 " + recipients + " (" + result + ")");
        } catch (Exception e) {
            log.error("NotifyOwner 失败: {}", e.getMessage(), e);
            return ExecutionResult.failed(e.getMessage());
        }
    }

    private List<String> parseRecipients(String payload) {
        List<String> out = new ArrayList<>();
        if (payload == null || payload.isBlank()) return out;
        try {
            JsonNode n = mapper.readTree(payload);
            JsonNode r = n.get("recipients");
            if (r != null && r.isArray()) {
                Iterator<JsonNode> it = r.elements();
                while (it.hasNext()) {
                    String s = it.next().asText("").trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private String buildHtml(OpsIncident incident, List<OpsInvestigationLog> investigations, String rationale) {
        StringBuilder sb = new StringBuilder(8 * 1024);
        sb.append("<html><body style='font-family:Helvetica,Arial,sans-serif;font-size:14px;color:#222'>");
        sb.append("<h2>事故诊断报告</h2>");
        sb.append("<table style='border-collapse:collapse'>");
        row(sb, "Incident ID", incident.getId());
        row(sb, "Service",     safe(incident.getServiceName(), "-"));
        row(sb, "Severity",    safe(incident.getSeverity(),    "-"));
        row(sb, "Status",      safe(incident.getStatus(),      "-"));
        row(sb, "Occurred",    String.valueOf(incident.getOccurredAt()));
        row(sb, "Summary",     escape(safe(incident.getSummary(), "-")));
        sb.append("</table>");

        if (rationale != null && !rationale.isBlank()) {
            sb.append("<h3>升级理由 (Agent)</h3>");
            sb.append("<blockquote>").append(escape(rationale)).append("</blockquote>");
        }

        sb.append("<h3>各 Agent 调查记录</h3>");
        if (investigations.isEmpty()) {
            sb.append("<p><em>无调查日志</em></p>");
        } else {
            for (OpsInvestigationLog l : investigations) {
                sb.append("<details open style='margin:8px 0'>")
                  .append("<summary><b>").append(escape(l.getAgentName())).append("</b>")
                  .append(" — ").append(nz(l.getDurationMs(), 0L)).append("ms,")
                  .append(" tokens=").append(nz(l.getTotalTokens(), 0))
                  .append("</summary>")
                  .append("<pre style='background:#f6f8fa;padding:8px;border-radius:4px;white-space:pre-wrap'>")
                  .append(escape(truncate(l.getOutputText(), 6000)))
                  .append("</pre></details>");
            }
        }
        sb.append("<hr/><p style='color:#888;font-size:12px'>本邮件由 AIOps 智能运维平台自动发送</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private void row(StringBuilder sb, String k, String v) {
        sb.append("<tr><td style='padding:4px 12px;color:#888'>").append(k).append("</td>")
          .append("<td style='padding:4px 12px'>").append(escape(v == null ? "" : v)).append("</td></tr>");
    }

    private String safe(String s, String def) { return s == null || s.isBlank() ? def : s; }
    private Object nz(Object v, Object def) { return v == null ? def : v; }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "\n...[truncated]" : s;
    }
}
