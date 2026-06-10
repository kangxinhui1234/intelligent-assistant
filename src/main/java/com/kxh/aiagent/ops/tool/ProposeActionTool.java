package com.kxh.aiagent.ops.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.ops.action.ActionType;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.entity.OpsIncident;
import com.kxh.aiagent.ops.mapper.OpsActionLogMapper;
import com.kxh.aiagent.ops.mapper.OpsIncidentMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent → HITL 桥接工具。
 * RootCauseAgent 完成诊断后,通过此工具产出一个"建议动作"提议:
 *   ① 写 ops_action_log (status=PENDING, 含 approve_token)
 *   ② 给 reviewers 发审批邮件 (带 magic-link 一键批准/驳回)
 *   ③ 返回给 Agent 一个简短确认串
 * <p>
 * 真正执行由 OpsActionController 在审批通过后异步触发。
 */
@Component
public class ProposeActionTool {

    private static final Logger log = LoggerFactory.getLogger(ProposeActionTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Resource(name = "opsActionLogMapper")
    private OpsActionLogMapper actionMapper;

    @Resource(name = "opsIncidentMapper")
    private OpsIncidentMapper incidentMapper;

    @Resource
    private EmailNotifyTool emailTool;

    @Value("${ops.approval.base-url:http://localhost:8092/api}")
    private String baseUrl;

    @Tool(description = """
            产出一个 HITL 待审批动作。仅在 RootCauseAgent 给出明确根因之后调用一次。
            支持的 actionType (必须精确匹配):
              - SUPPRESS_FINGERPRINT : 屏蔽同 fingerprint 的告警 N 小时。
                  paramsJson 示例: {"hours": 4}
              - NOTIFY_OWNER         : 把完整诊断报告升级邮件给指定负责人。
                  paramsJson 示例: {"recipients": ["sre@xx.com"]}
              - MARK_AS_KNOWN        : 登记为已知问题,写入 Milvus 历史 RAG,后续类似告警可命中。
                  paramsJson 示例: {"errorClass": "java.lang.NullPointerException", "resolution": "..."}
            返回值是工单确认信息(含 actionId / 审批链接),Agent 不要再做其他动作。""")
    public String proposeAction(
            @ToolParam(description = "事故 ID, 必须从输入的 incidentId 字段原样传入") String incidentId,
            @ToolParam(description = "动作类型, 严格枚举: SUPPRESS_FINGERPRINT / NOTIFY_OWNER / MARK_AS_KNOWN") String actionType,
            @ToolParam(description = "目标摘要,如 fingerprint 或 收件人列表,用于人工快速识别") String target,
            @ToolParam(description = "提议参数的 JSON 字符串 (严格 JSON,允许为空字符串)") String paramsJson,
            @ToolParam(description = "提议理由 — Agent 必须写清楚为什么建议这个动作,200-500 字") String rationale) {

        ActionType type = ActionType.fromString(actionType);
        if (type == null) {
            return "error: unknown actionType=" + actionType
                    + " (expected SUPPRESS_FINGERPRINT / NOTIFY_OWNER / MARK_AS_KNOWN)";
        }
        OpsIncident incident = (incidentId == null) ? null : incidentMapper.selectById(incidentId);
        if (incident == null) {
            return "error: incident_not_found id=" + incidentId;
        }

        // payload 兜底校验: 至少能 parse 成 JSON
        String payload = (paramsJson == null || paramsJson.isBlank()) ? "{}" : paramsJson.trim();
        try {
            mapper.readTree(payload);
        } catch (Exception e) {
            payload = "{\"_raw\":" + quote(paramsJson) + "}";
            log.warn("proposeAction: paramsJson 不是合法 JSON,已包装 _raw");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        OpsActionLog row = new OpsActionLog();
        row.setIncidentId(incidentId);
        row.setActionType(type.name());
        row.setTarget(truncate(target, 256));
        row.setProposalPayload(payload);
        row.setRationale(truncate(rationale, 4000));
        row.setStatus("PENDING");
        row.setApproveToken(token);
        row.setTriggeredBy("RootCauseAgent");
        row.setTriggeredAt(LocalDateTime.now());
        actionMapper.insert(row);

        try {
            sendApprovalEmail(incident, row);
        } catch (Exception e) {
            log.warn("发审批邮件失败: {}", e.getMessage());
        }

        Map<String, Object> ret = new HashMap<>();
        ret.put("actionId", row.getId());
        ret.put("status", row.getStatus());
        ret.put("approveUrl", approveUrl(row.getId(), token));
        ret.put("rejectUrl",  rejectUrl(row.getId(), token));
        ret.put("hint", "已生成审批工单,等待人工批准。请勿再次调用本工具。");
        try {
            return mapper.writeValueAsString(ret);
        } catch (Exception e) {
            return "proposed actionId=" + row.getId() + " status=PENDING";
        }
    }

    private void sendApprovalEmail(OpsIncident incident, OpsActionLog row) {
        var recipients = emailTool.defaultReviewers();
        if (recipients.isEmpty()) {
            log.info("[ApprovalMail Mock] reviewers 未配置, 仅落库 actionId={}", row.getId());
            return;
        }
        String subject = "[AIOps 待审批] " + row.getActionType()
                + " · " + safe(incident.getServiceName(), "unknown")
                + " · " + safe(incident.getSeverity(), "P1");
        String html = buildHtml(incident, row);
        emailTool.sendHtml(recipients, subject, html);
    }

    private String buildHtml(OpsIncident incident, OpsActionLog row) {
        String approve = approveUrl(row.getId(), row.getApproveToken());
        String reject  = rejectUrl(row.getId(), row.getApproveToken());
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<html><body style='font-family:Helvetica,Arial,sans-serif;font-size:14px;color:#222'>");
        sb.append("<h2>🤖 AIOps 智能体提议一个动作,等待你的审批</h2>");
        sb.append("<table style='border-collapse:collapse'>");
        kv(sb, "Action #",     String.valueOf(row.getId()));
        kv(sb, "Action Type",  row.getActionType());
        kv(sb, "Target",       safe(row.getTarget(), "-"));
        kv(sb, "Incident",     incident.getId());
        kv(sb, "Service",      safe(incident.getServiceName(), "-"));
        kv(sb, "Severity",     safe(incident.getSeverity(), "-"));
        kv(sb, "Summary",      escape(safe(incident.getSummary(), "-")));
        kv(sb, "Payload",      "<code>" + escape(safe(row.getProposalPayload(), "{}")) + "</code>");
        sb.append("</table>");
        sb.append("<h3>提议理由</h3>");
        sb.append("<blockquote style='border-left:3px solid #ddd;padding-left:12px;color:#444'>")
          .append(escape(safe(row.getRationale(), "-")))
          .append("</blockquote>");
        sb.append("<p style='margin-top:24px'>");
        sb.append("<a href='").append(approve)
          .append("' style='display:inline-block;padding:10px 20px;margin-right:12px;")
          .append("background:#1f883d;color:#fff;border-radius:6px;text-decoration:none'>✅ 批准并执行</a>");
        sb.append("<a href='").append(reject)
          .append("' style='display:inline-block;padding:10px 20px;")
          .append("background:#cf222e;color:#fff;border-radius:6px;text-decoration:none'>❌ 驳回</a>");
        sb.append("</p>");
        sb.append("<p style='color:#888;font-size:12px'>链接含一次性 token,48 小时有效。也可登录管理页 ")
          .append("<a href='").append(baseUrl).append("/ops/actions.html'>").append(baseUrl).append("/ops/actions.html</a> 操作。</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private void kv(StringBuilder sb, String k, String v) {
        sb.append("<tr><td style='padding:4px 12px;color:#888'>").append(k).append("</td>")
          .append("<td style='padding:4px 12px'>").append(v == null ? "" : v).append("</td></tr>");
    }

    private String approveUrl(Long id, String token) {
        return baseUrl + "/ops/actions/" + id + "/approve-page?token=" + token;
    }
    private String rejectUrl(Long id, String token) {
        return baseUrl + "/ops/actions/" + id + "/reject-page?token=" + token;
    }
    private String safe(String s, String def) { return s == null || s.isBlank() ? def : s; }
    private String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() > n ? s.substring(0, n) : s;
    }
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
