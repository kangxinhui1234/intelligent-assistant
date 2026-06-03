package com.kxh.aiagent.ops.tool;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 企业微信群机器人通知工具
 * <p>
 * Webhook 申请方式: 群聊右上角 -> 群机器人 -> 添加机器人 -> 复制 webhook URL
 * 格式形如: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxxxxxx
 * <p>
 * 限制: 单条消息 markdown content <= 4096 字节, 单个机器人 20 条/分钟
 */
@Component
public class WechatWorkNotifyTool {

    private static final Logger log = LoggerFactory.getLogger(WechatWorkNotifyTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_BYTES = 4000;

    @Value("${ops.notify.wechat-work.webhook:}")
    private String webhook;

    @Tool(description = """
            推送诊断报告到企业微信群机器人。传入 Markdown 格式的标题和正文。
            如果未配置 webhook，仅打印到日志(Mock 模式)。""")
    public String notifyWechatWork(
            @ToolParam(description = "消息标题，简短一句话") String title,
            @ToolParam(description = "Markdown 正文") String markdownContent) {

        String fullContent = "# " + title + "\n\n" + markdownContent;
        // 字节截断防止超 4096
        if (fullContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            fullContent = truncateUtf8(fullContent, MAX_BYTES);
        }

        if (webhook == null || webhook.isBlank()) {
            log.info("[WechatWork Mock] title={}\n{}", title, markdownContent);
            return "notify_mocked: webhook not configured";
        }
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "markdown");
            ObjectNode markdown = root.putObject("markdown");
            markdown.put("content", fullContent);

            String body = mapper.writeValueAsString(root);
            HttpResponse resp = HttpRequest.post(webhook)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body(body)
                    .timeout(5000)
                    .execute();
            String respBody = resp.body();
            log.info("WechatWork 推送响应: {}", respBody);
            return "notify_sent: " + respBody;
        } catch (Exception e) {
            log.error("WechatWork 推送失败: {}", e.getMessage(), e);
            return "notify_failed: " + e.getMessage();
        }
    }

    private String truncateUtf8(String s, int maxBytes) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return s;
        // 保险截断到 maxBytes 之前的有效 UTF-8 边界
        int cut = maxBytes;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) cut--;
        return new String(bytes, 0, cut, java.nio.charset.StandardCharsets.UTF_8)
                + "\n\n_(已截断)_";
    }
}
