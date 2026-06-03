package com.kxh.aiagent.ops.dedup;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * 指纹生成器 —— 唯一在 LLM 之前要做的"结构化"工作。
 * 目的：把"同根因不同次告警"识别为相同指纹，用于 5min 窗口去重。
 *
 * 不做字段提取，不做格式解析，那些是 AlertParserAgent 的活。
 */
@Component
public class FingerprintGenerator {

    private static final Pattern DIGIT_SEQ = Pattern.compile("\\d{4,}");
    private static final Pattern UUID_LIKE = Pattern.compile("[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}");
    private static final Pattern HEX_LONG = Pattern.compile("[0-9a-f]{16,}");
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]?\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * 基于告警原文的指纹。
     * 归一化策略: 移除时间戳/UUID/长十六进制/长数字/多余空白,然后取前 512 字符哈希。
     * 这样"同样的 NPE 在不同时间不同主机不同 trace"得到相同指纹。
     */
    public String fromAlertText(String text) {
        if (text == null || text.isBlank()) return sha256("empty");
        String s = text;
        s = TIMESTAMP.matcher(s).replaceAll("<T>");
        s = UUID_LIKE.matcher(s).replaceAll("<UUID>");
        s = HEX_LONG.matcher(s).replaceAll("<HEX>");
        s = DIGIT_SEQ.matcher(s).replaceAll("<N>");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        if (s.length() > 512) s = s.substring(0, 512);
        return sha256(s);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
