package com.kxh.aiagent.tools;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网页抓取
 */
public class WebPageScape {


    @Tool(description = "scrape given web page content (cleaned & summarized, length-limited)")
    public String scapeWebPage(@ToolParam(description = "http url of scrape content") String url) {
        final int TIMEOUT_MS = 15000;          // 15s
        final int MAX_BODY_BYTES = 3 * 1024 * 1024; // 3MB 防止超大页面
        final int MAX_RETURN_CHARS = 3000;     // 返回给大模型的最大字符数（可按需调小）
        try {
            if (url == null || url.isBlank()) {
                return "{\"error\":\"Empty URL\"}";
            }

            // 构建请求（User-Agent/超时/大小限制/重定向）
            org.jsoup.Connection conn = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; OpenManusBot/1.0; +https://example.com/bot)")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .maxBodySize(MAX_BODY_BYTES)
                    .ignoreContentType(true); // 有些站的 Content-Type 不标准

            Document doc = conn.get();

            // 元信息
            String finalUrl = conn.response().url().toString();
            int status = conn.response().statusCode();
            String contentType = conn.response().contentType();
            String title = doc.title();

            // 清洗：移除非正文噪音
            doc.select("script, style, noscript, svg, canvas, iframe, footer, header, nav, form, aside").remove();
            // 常见广告/无关容器（可按需扩展）
            doc.select("[class*=advert],[id*=advert],[class*=ads],[id*=ads]").remove();

            // 主体选择优先级：article > main > #content/.content > body
            Element main = doc.selectFirst("article");
            if (main == null) main = doc.selectFirst("main");
            if (main == null) main = doc.selectFirst("#content, .content, #main-content, .article, #article");
            if (main == null) main = doc.body();

            // 段落抽取与排序（按长度/信息密度）
            List<String> paras = new ArrayList<>();
            for (Element p : main.select("p")) {
                String t = p.text().trim();
                if (t.length() >= 40) { // 过滤超短/噪音段
                    paras.add(t);
                }
            }
            // 兜底：没有 p，就取整块文本
            if (paras.isEmpty()) {
                String bodyText = main.text().trim();
                if (bodyText.isEmpty()) {
                    return "{\"error\":\"Empty content after cleaning\",\"url\":\"" + escape(finalUrl) + "\"}";
                }
                paras.add(bodyText);
            } else {
                // 简单的“主内容优先”策略：按长度降序选取前若干段
                paras.sort((a, b) -> Integer.compare(b.length(), a.length()));
            }

            // 组装摘要，限长
            StringBuilder content = new StringBuilder();
            int used = 0;
            for (String p : paras) {
                if (used >= MAX_RETURN_CHARS) break;
                int remain = MAX_RETURN_CHARS - used;
                String add = p.length() > remain ? p.substring(0, remain) : p;
                content.append(add).append("\n\n");
                used += add.length() + 2;
            }
            boolean truncated = used >= MAX_RETURN_CHARS;

            // 额外提取：前几个外链信息（可选）
            List<String> links = new ArrayList<>();
            for (Element a : main.select("a[href]")) {
                String href = a.absUrl("href");
                String txt = a.text();
                if (!href.isBlank() && links.size() < 10) {
                    links.add((txt.isBlank() ? href : (txt + " - " + href)));
                }
            }

            // 返回结构化字符串（JSON 字符串，避免引入额外依赖）
            StringBuilder json = new StringBuilder();
            json.append("{")
                    .append("\"url\":\"").append(escape(finalUrl)).append("\",")
                    .append("\"status\":").append(status).append(",")
                    .append("\"contentType\":\"").append(escape(contentType)).append("\",")
                    .append("\"title\":\"").append(escape(title)).append("\",")
                    .append("\"truncated\":").append(truncated).append(",")
                    .append("\"contentLength\":").append(content.length()).append(",")
                    .append("\"content\":\"").append(escape(content.toString())).append("\",");

            // 简单输出 links
            json.append("\"links\":[");
            for (int i = 0; i < links.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escape(links.get(i))).append("\"");
            }
            json.append("]}");

            return json.toString();

        } catch (IOException e) {
            return "{\"error\":\"Error scraping web page: " + escape(e.getMessage()) + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"Unexpected error: " + escape(e.getMessage()) + "\"}";
        }
    }

    // 简单 JSON 转义（避免引入依赖）
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

}