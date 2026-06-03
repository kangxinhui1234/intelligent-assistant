package com.kxh.aiagent.ops.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 本地文件通知工具 —— 把诊断报告写入本地 markdown 文件。
 * 适合本地调试 / 个人使用，不需要任何外部服务配置。
 * 文件路径: {user.dir}/tmp/diagnosis/yyyyMMdd-HHmmss-{title}.md
 */
@Component
public class LocalFileNotifyTool {

    private static final Logger log = LoggerFactory.getLogger(LocalFileNotifyTool.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Value("${ops.notify.local-file.dir:tmp/diagnosis}")
    private String outputDir;

    @Tool(description = """
            把诊断报告写入本地 markdown 文件。返回文件绝对路径。
            适合本地调试，用任何 markdown 编辑器(VSCode/Typora)即可查看。""")
    public String notifyLocalFile(
            @ToolParam(description = "消息标题，简短一句话") String title,
            @ToolParam(description = "Markdown 正文") String markdownContent) {
        try {
            Path baseDir = Paths.get(System.getProperty("user.dir"), outputDir);
            Files.createDirectories(baseDir);

            String safeTitle = sanitize(title);
            String filename = LocalDateTime.now().format(FMT) + "-" + safeTitle + ".md";
            Path file = baseDir.resolve(filename);

            String content = "# " + title + "\n\n"
                    + "_生成时间: " + LocalDateTime.now() + "_\n\n"
                    + "---\n\n"
                    + markdownContent;
            Files.writeString(file, content, StandardCharsets.UTF_8);

            String abs = file.toAbsolutePath().toString();
            log.info("[LocalFile] 诊断报告已写入: {}", abs);
            return "notify_saved: " + abs;
        } catch (Exception e) {
            log.error("写入本地文件失败: {}", e.getMessage(), e);
            return "notify_failed: " + e.getMessage();
        }
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "diagnosis";
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|\\s\\[\\]]", "_");
        if (cleaned.length() > 60) cleaned = cleaned.substring(0, 60);
        return cleaned;
    }
}
