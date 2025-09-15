package com.kxh.aiagent.agent;

import com.kxh.aiagent.advisor.MyCustomAdvisor;
import com.kxh.aiagent.model.AgentState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 投资智能体
 */
@Component
public class InvestManus extends ToolCallAgent{

    public InvestManus(ToolCallback[] toolCallbacks, ChatModel dashscopeChatModel,Advisor loveAppRagCloudAdvisor) {
        super(toolCallbacks);
        this.setState(AgentState.IDLE);
        setChatClient(ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyCustomAdvisor())
//                .defaultAdvisors(loveAppRagCloudAdvisor) // 检索增强
                .build());
        String SystemPrompt = """
    You are InvestGPT, a highly capable investment research AI assistant, 
    specialized in analyzing financial statements, valuation metrics, 
    market trends, and generating professional investment research reports. 
    You have access to various tools including:
      - Financial Data Retrieval (historical and current financial statements)
      - Valuation Metrics Retrieval (PE, PB, PS, etc.)
      - Visualization Tools (charts, trend analysis)
    When responding to user requests:
      - First, interpret the user's query and identify the data and tools needed.
      - Produce outputs in a format suitable for inclusion in professional investment reports.
""";

        String nextPrompt= """
               Based on user needs, you can answer by your knowledge,or select the  appropriate tool . For complex tasks,
                you can break down the problem and use different tools step by step to solve it. 你选择一个工具同时能告诉我选择他的一些前因后果，不要选择一个工具，然后一句话不说
                . 最后请用工具生成一分markdown文档，生成最终的投资分析及建议,请把markdown下载地址告诉我
               If you want to stop the interaction at any point, use the `terminate` tool/function call.
               """;

        this.setNextPrompt(nextPrompt);
        this.setSYSTEM_PROMPT(SystemPrompt);
        this.setMaxStep(8);
        this.setName("InvestAgent");

    }
}
