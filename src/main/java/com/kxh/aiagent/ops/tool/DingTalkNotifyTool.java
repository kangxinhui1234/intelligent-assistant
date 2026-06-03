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

@Component
public class DingTalkNotifyTool {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifyTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ops.notify.dingtalk.webhook:}")
    private String webhook;

    @Tool(description = """
            推送诊断报告到钉钉群。传入 Markdown 格式的标题和正文。
            如果未配置 webhook，仅打印到日志。""")
    public String notifyDingTalk(
            @ToolParam(description = "消息标题，简短一句话") String title,
            @ToolParam(description = "Markdown 正文") String markdownContent) {
        if (webhook == null || webhook.isBlank()) {
            log.info("[DingTalk Mock] title={}\n{}", title, markdownContent);
            return "notify_mocked: webhook not configured";
        }
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "markdown");
            ObjectNode markdown = root.putObject("markdown");
            markdown.put("title", title);
            markdown.put("text", "### " + title + "\n\n" + markdownContent);

            String body = mapper.writeValueAsString(root);
            HttpResponse resp = HttpRequest.post(webhook)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body(body)
                    .timeout(5000)
                    .execute();
            String respBody = resp.body();
            log.info("DingTalk 推送响应: {}", respBody);
            return "notify_sent: " + respBody;
        } catch (Exception e) {
            log.error("DingTalk 推送失败: {}", e.getMessage(), e);
            return "notify_failed: " + e.getMessage();
        }
    }
}
