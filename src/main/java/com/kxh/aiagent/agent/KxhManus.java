package com.kxh.aiagent.agent;

import com.kxh.aiagent.advisor.MyCustomAdvisor;
import com.kxh.aiagent.model.AgentState;
import com.kxh.aiagent.tools.manager.CompatibleToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 真正的智能体
 */
@Component
public class KxhManus extends ToolCallAgent {


    /**
     * 构造
     */
    public KxhManus (ToolCallback[] toolCallbacks, ChatModel dashscopeChatModel){
       super(toolCallbacks);
        this.setState(AgentState.IDLE);
        setChatClient(ChatClient.builder(dashscopeChatModel)
               .defaultAdvisors(new MyCustomAdvisor())
               .build());
       String SystemPrompt = """
               You are OpenManus, an all-capable AI assistant, 
               aimed at solving any task presented by the user. 
               You have various tools at your disposal that you can call upon to efficiently 
               complete complex requests.请返回你的思路，比如选择某个工具的原因
               """;
       String nextPrompt= """
               Based on user needs, proactively select the most appropriate tool or combination of tools. For complex tasks,
                you can break down the problem and use different tools step by step to solve it. After using each tool,
                 clearly explain the execution results and suggest the next steps.          
               If you want to stop the interaction at any point, use the `terminate` tool/function call.在你决定terminate的同时整合会话历史，返回一个最终的回答结果，让我知道最终回复
               """;

       this.setNextPrompt(nextPrompt);
       this.setSYSTEM_PROMPT(SystemPrompt);
       this.setMaxStep(8);
       this.setName("kxhAgent");



    }
}
