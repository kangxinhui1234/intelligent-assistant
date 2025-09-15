package com.kxh.aiagent.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class CustomerTools {

    @Tool(description = "Retrieve local customer information")
    String getCustomerInfo( Long id, ToolContext toolContext) {
        return toolContext.getContext().toString();
    }
}
