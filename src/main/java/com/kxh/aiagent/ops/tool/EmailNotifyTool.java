package com.kxh.aiagent.ops.tool;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 邮件通知工具 — 给 Agent 和 HITL 审批流程共用。
 * <p>
 * 1) Agent 可作为 Tool 调用 {@link #notifyEmail(String, String)},把诊断报告发给配置好的 reviewers
 * 2) 内部组件 (ProposeActionTool / OpsActionController) 直接调 {@link #sendHtml(List, String, String)}
 *    发审批邮件 / 升级通知 / 结果回执
 * <p>
 * 若 spring.mail.username 未配置,所有发送转为 Mock(只打日志,不抛错),便于本地无 SMTP 时不阻塞流水线。
 */
@Component
public class EmailNotifyTool {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifyTool.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${ops.notify.email.from:}")
    private String fromAddress;

    @Value("${ops.notify.email.reviewers:}")
    private String reviewersCsv;

    @Tool(description = """
            把诊断报告邮件推送给已配置的审批人/服务负责人列表(ops.notify.email.reviewers)。
            如果未配置 SMTP,仅打印日志(Mock 模式)。
            适合作为流水线产出最终报告后的通知出口。""")
    public String notifyEmail(
            @ToolParam(description = "邮件主题,简短一句话") String title,
            @ToolParam(description = "HTML 正文(也可传 markdown,会被原样发送)") String htmlContent) {
        List<String> recipients = parseReviewers();
        if (recipients.isEmpty()) {
            log.info("[Email Mock] reviewers 未配置 title={}", title);
            return "notify_mocked: reviewers not configured";
        }
        return sendHtml(recipients, title, htmlContent);
    }

    /**
     * 通用 HTML 邮件发送 — 给非 Agent 调用方使用。
     *
     * @return "sent: ..." / "mocked: ..." / "failed: ..."
     */
    public String sendHtml(List<String> recipients, String subject, String htmlBody) {
        if (recipients == null || recipients.isEmpty()) {
            return "skipped: no recipients";
        }
        if (mailSender == null || smtpUsername == null || smtpUsername.isBlank()) {
            log.info("[Email Mock] SMTP 未配置, 模拟发送 to={} subject={}\n{}",
                    recipients, subject, truncate(htmlBody, 500));
            return "mocked: smtp not configured";
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(effectiveFrom());
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("[Email] 已发送 to={} subject={}", recipients, subject);
            return "sent: " + recipients;
        } catch (Exception e) {
            log.error("[Email] 发送失败 to={} subject={}: {}", recipients, subject, e.getMessage(), e);
            return "failed: " + e.getMessage();
        }
    }

    public List<String> defaultReviewers() {
        return parseReviewers();
    }

    private String effectiveFrom() {
        return (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : smtpUsername;
    }

    private List<String> parseReviewers() {
        if (reviewersCsv == null || reviewersCsv.isBlank()) return List.of();
        return Arrays.stream(reviewersCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "...[truncated]" : s;
    }
}
