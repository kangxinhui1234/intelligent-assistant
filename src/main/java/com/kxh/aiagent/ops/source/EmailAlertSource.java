package com.kxh.aiagent.ops.source;

import com.kxh.aiagent.ops.service.IncidentProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 邮件 IMAP 告警源。默认 enabled=false,需显式开启。
 * 开启后由四道闸门控制速率:
 * - poll-interval: 轮询间隔(默认 10min)
 * - max-per-poll : 单次最多 N 封 (默认 1)
 * - max-per-hour : 每小时上限 (默认 5)
 * - max-per-day  : 每天上限 (默认 30)
 * <p>
 * dry-run=true 时仅解析邮件不调流水线。
 */
@Component
@ConditionalOnProperty(prefix = "ops.source.email", name = "enabled", havingValue = "true")
public class EmailAlertSource implements AlertSource {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertSource.class);

    @Value("${ops.source.email.host:imap.qq.com}")
    private String host;
    @Value("${ops.source.email.port:993}")
    private int port;
    @Value("${ops.source.email.protocol:imaps}")
    private String protocol;
    @Value("${ops.source.email.username:}")
    private String username;
    @Value("${ops.source.email.password:}")
    private String password;
    @Value("${ops.source.email.folder:INBOX}")
    private String folder;
    @Value("${ops.source.email.mark-as-read:true}")
    private boolean markAsRead;
    @Value("${ops.source.email.dry-run:false}")
    private boolean dryRun;
    @Value("${ops.source.email.sender-whitelist:}")
    private String senderWhitelistRaw;
    @Value("${ops.source.email.subject-pattern:}")
    private String subjectPatternRaw;

    @Resource private EmailRateLimiter rateLimiter;
    @Resource private IncidentProcessor processor;

    private List<String> senderWhitelist;
    private Pattern subjectPattern;

    @PostConstruct
    public void init() {
        senderWhitelist = senderWhitelistRaw == null || senderWhitelistRaw.isBlank()
                ? List.of()
                : List.of(senderWhitelistRaw.split(","));
        subjectPattern = subjectPatternRaw == null || subjectPatternRaw.isBlank()
                ? null
                : Pattern.compile(subjectPatternRaw);
        log.info("EmailAlertSource 已启用: host={} user={} folder={} dryRun={} senderWhitelist={} subjectPattern={}",
                host, mask(username), folder, dryRun, senderWhitelist, subjectPatternRaw);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("EmailAlertSource: 用户名或密码未配置,轮询会失败");
        }
    }

    @Override
    public String sourceType() {
        return "email";
    }

    @Scheduled(fixedDelayString = "${ops.source.email.poll-interval-ms:600000}", initialDelay = 10000)
    public void poll() {
        int quota = rateLimiter.remainingQuota();
        if (quota <= 0) {
            var snap = rateLimiter.snapshot();
            log.info("Email quota 已耗尽,本轮跳过 hour={}/{} day={}/{}",
                    snap.hourUsed(), snap.hourMax(), snap.dayUsed(), snap.dayMax());
            return;
        }
        log.info("EmailAlertSource 轮询开始,本轮可处理上限 {} 封", quota);

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", port);
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "30000");

        Session session = Session.getInstance(props);
        try (Store store = session.getStore(protocol)) {
            store.connect(host, port, username, password);
            try (Folder f = store.getFolder(folder)) {
                f.open(Folder.READ_WRITE);

                // 服务器端先按 "未读 + 发件人" 过滤,大幅减少返回量
                SearchTerm searchTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
                if (!senderWhitelist.isEmpty()) {
                    SearchTerm fromTerm = new FromStringTerm(senderWhitelist.get(0).trim());
                    for (int i = 1; i < senderWhitelist.size(); i++) {
                        fromTerm = new OrTerm(fromTerm, new FromStringTerm(senderWhitelist.get(i).trim()));
                    }
                    searchTerm = new AndTerm(searchTerm, fromTerm);
                }

                Message[] candidates;
                try {
                    candidates = f.search(searchTerm);
                } catch (Exception se) {
                    log.error("IMAP search 失败,本轮跳过: {}", se.getMessage());
                    return;
                }
                log.info("IMAP 命中候选邮件: {} 封 (服务器端已按发件人过滤)", candidates.length);

                int processed = 0;
                int skipped = 0;
                for (Message msg : candidates) {
                    if (processed >= quota) break;
                    try {
                        if (!subjectPassFilter(msg)) {
                            skipped++;
                            continue;
                        }
                        handleOne(msg);
                        if (markAsRead) {
                            try { msg.setFlag(Flags.Flag.SEEN, true); } catch (Exception ignored) {}
                        }
                        rateLimiter.recordOne();
                        processed++;
                    } catch (Exception perMsg) {
                        // 单封异常不影响其他邮件
                        log.warn("单封邮件处理失败,跳过: {}", perMsg.getMessage());
                        skipped++;
                    }
                }
                log.info("EmailAlertSource 轮询完成: 处理={} 跳过={} 候选={}", processed, skipped, candidates.length);
            }
        } catch (Exception e) {
            log.error("EmailAlertSource 轮询失败: {}", e.getMessage(), e);
        }
    }

    /** 主题过滤(已在 passFilters 中,这里抽出方便单封 try-catch) */
    private boolean subjectPassFilter(Message msg) throws MessagingException {
        if (subjectPattern == null) return true;
        String subject = msg.getSubject();
        if (subject == null || !subjectPattern.matcher(subject).find()) {
            log.debug("邮件被主题正则过滤掉: subject={}", subject);
            return false;
        }
        return true;
    }

    private void handleOne(Message msg) throws MessagingException, IOException {
        String subject = msg.getSubject() == null ? "" : msg.getSubject();
        String body = extractBody(msg);
        Map<String, Object> headers = new HashMap<>();
        Address[] froms = msg.getFrom();
        if (froms != null && froms.length > 0) {
            headers.put("from", froms[0].toString());
        }

        log.info("处理邮件: subject={}", trunc(subject, 80));
        if (dryRun) {
            log.info("[dry-run] 解析完成,不调流水线。body length={}", body.length());
            return;
        }

        RawAlert raw = new RawAlert("email", subject, body, headers, Instant.now());
        var outcome = processor.process(raw, null, java.util.UUID.randomUUID().toString());
        log.info("邮件 → 流水线结果: status={} incidentId={} detail={}",
                outcome.status(),
                outcome.incident() == null ? "-" : outcome.incident().incidentId(),
                outcome.detail());
    }

    private String extractBody(Message msg) throws MessagingException, IOException {
        Object content = msg.getContent();
        if (content instanceof String s) return s;
        if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    sb.append(part.getContent().toString()).append('\n');
                } else if (part.isMimeType("text/html") && sb.length() == 0) {
                    sb.append(part.getContent().toString()).append('\n');
                }
            }
            return sb.toString();
        }
        return content == null ? "" : content.toString();
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "***";
        return s.substring(0, 2) + "***" + s.substring(s.length() - 2);
    }

    private String trunc(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
