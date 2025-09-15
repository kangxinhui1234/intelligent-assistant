package com.kxh.aiagent.tools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MarkdownGenerationTool {

    /**
     * 生成 Markdown 文件（简化版）
     *
     * @param fileName 文件名（不需要扩展名）
     * @param markdownContent 完整的 Markdown 内容
     * @return 生成文件的路径
     */
    @Tool(description = "这是一个工具，可以用来生成markdown文档，并获取一个http地址作为出参，比如：http://localhost:8092/api/api/files/金证股份投资分析报告.md")
    public String generateMarkdown(@ToolParam(description = "文档名称") String fileName, @ToolParam(description = "文档的内容") String markdownContent) {
        return generateMarkdown(fileName, markdownContent, false);
    }

    /**
     * 生成 Markdown 文件（带时间戳选项）
     *
     * @param fileName 文件名（不需要扩展名）
     * @param markdownContent 完整的 Markdown 内容
     * @param addTimestamp 是否在文件名中添加时间戳
     * @return 生成文件的路径
     */
    public String generateMarkdown(String fileName, String markdownContent, boolean addTimestamp) {
        // 创建目录
        String fileDir = System.getProperty("user.dir") + "/tmp/markdown";
        Path dirPath = Paths.get(fileDir);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + fileDir, e);
        }

        // 处理文件名
        if (addTimestamp) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            fileName = fileName.replace(".md", "") + "_" + timestamp + ".md";
        } else if (!fileName.toLowerCase().endsWith(".md")) {
            fileName += ".md";
        }

        String filePath = fileDir + "/" + fileName;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // 直接写入 AI 生成的 Markdown 内容
            writer.write(markdownContent);
            return "Markdown 文件已生成: " + "http://localhost:8092/api/api/files/"+fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Markdown file: " + filePath, e);
        }
    }

    /**
     * 批量生成 Markdown 文件
     *
     * @param files 文件名和内容的映射
     * @return 生成结果信息
     */
    public String generateMultipleMarkdownFiles(java.util.Map<String, String> files) {
        StringBuilder result = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : files.entrySet()) {
            String fileResult = generateMarkdown(entry.getKey(), entry.getValue());
            result.append(fileResult).append("\n");
        }
        return result.toString();
    }

    /**
     * 生成带分类的 Markdown 文件
     *
     * @param category 分类目录
     * @param fileName 文件名
     * @param markdownContent Markdown 内容
     * @return 生成文件的路径
     */
    public String generateCategorizedMarkdown(String category, String fileName, String markdownContent) {
        // 创建分类目录
        String fileDir = System.getProperty("user.dir") + "/tmp/markdown/" + category;
        Path dirPath = Paths.get(fileDir);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + fileDir, e);
        }

        // 确保文件名有 .md 扩展名
        if (!fileName.toLowerCase().endsWith(".md")) {
            fileName += ".md";
        }

        String filePath = fileDir + "/" + fileName;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(markdownContent);
            return "Markdown 文件已生成: " + filePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Markdown file: " + filePath, e);
        }
    }
}