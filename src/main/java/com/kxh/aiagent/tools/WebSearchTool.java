package com.kxh.aiagent.tools;


import cn.hutool.http.HttpUtil;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页检索工具
 */
public class WebSearchTool {
    @Tool(description = "search info from  web internet")
    public String searchWebByKeyword(@ToolParam(description = "Keyword of search") String queryKeyword){
        String queryTemplate = "https://www.searchapi.io/api/v1/search?api_key=Vov6CHgXy4sNDs8pvG5DTbYv&engine=google_ads_transparency_center_advertiser_search&q="+queryKeyword;
        String response = HttpUtil.get(queryTemplate);
        return response;

    }
}
