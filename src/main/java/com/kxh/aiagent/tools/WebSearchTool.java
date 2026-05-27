package com.kxh.aiagent.tools;

import cn.hutool.http.HttpUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class WebSearchTool {

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "search info from web internet")
    public String searchWebByKeyword(@ToolParam(description = "Keyword of search") String queryKeyword) {
        if (queryKeyword == null || queryKeyword.isBlank()) {
            return "search keyword cannot be empty";
        }
        String encoded = java.net.URLEncoder.encode(queryKeyword, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://www.searchapi.io/api/v1/search?api_key=" + apiKey + "&engine=google&q=" + encoded;
        return HttpUtil.get(url);
    }
}
