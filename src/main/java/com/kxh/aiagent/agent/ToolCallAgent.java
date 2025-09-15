package com.kxh.aiagent.agent;

import cn.hutool.log.Log;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.kxh.aiagent.advisor.MyCustomAdvisor;
import com.kxh.aiagent.enums.USER_MESSAGE_TYPE;
import com.kxh.aiagent.model.AgentState;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import javax.security.auth.callback.CallbackHandler;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实现推理 思考的能力
 */
@Slf4j
public class ToolCallAgent extends ReActAgent{

    private ToolCallback[] toolCallbacks;

    private ChatResponse toolChatResponse;
    /**
     * 工具调用管理者
     */
    private ToolCallingManager toolCallingManager;

    /**
     * 工具管理上下文
     * @return
     */
    private ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] toolCallbacks){
        this.toolCallbacks = toolCallbacks;
        DashScopeChatOptions chatOptions = DashScopeChatOptions.builder().withInternalToolExecutionEnabled(false).build();

        this.chatOptions = chatOptions;
        // 创建工具调用管理器
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
        this.toolCallingManager = toolCallingManager;

    }

    @Override
    public boolean think() {
        // 追加重复提示消息给大模型，确保大模型不会遗忘规则 如果上一条还是用户系统提示，不追加
        if(getNextPrompt() != null && !"".equals(getNextPrompt())
                && getMessageList().get(getMessageList().size()-1).getMetadata().get("userMessageType") != USER_MESSAGE_TYPE.USER_SYSTEM){
            UserMessage userMessage = new UserMessage(getNextPrompt());
            userMessage.getMetadata().put("userMessageType", USER_MESSAGE_TYPE.USER_SYSTEM);
            getMessageList().add(userMessage);
        }
        try {
            /**
             * 构造prompt
             */
            Prompt prompt = new Prompt(getMessageList(), chatOptions);
             toolChatResponse = getChatClient().prompt(prompt).toolCallbacks(toolCallbacks)
                   // .advisors(new MyCustomAdvisor())
                    .system(this.getSYSTEM_PROMPT())
                    .call()
                    .chatResponse();
            AssistantMessage assistantMessage = toolChatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallbacks1 = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            log.info(getName() + "思考：" + result);
            log.info(getName() + "选择了" + toolCallbacks1.size() + "个工具");
            String tools = toolCallbacks1.stream().
                    map(toolCall -> String.format("工具名称：%s,参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(tools);
            if (toolCallbacks1.size() > 0) {
                return true;
            } else {
                getMessageList().add(assistantMessage);
            }
            // 循环检测
            if(this.isStrunk()){
               this.handleStrunkState(); //处理循环状态
            }
        }catch (Exception e){
            log.error("处理时遇到错误："+e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到错误："+e.getMessage()));
        }
        return false;
    }

    @Override
    public String act() {
        String results = "";
        try{
        /**
         * 执行工具调用
         */
        Prompt prompt = new Prompt(getMessageList(),chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt,toolChatResponse);
         setMessageList(toolExecutionResult.conversationHistory()); // 包括助手消息和工具响应 包括历史所有会话消息
        // 打印所有工具的调用接口 返回
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage)toolExecutionResult.conversationHistory().get(toolExecutionResult.conversationHistory().size()-1);
        // toolResponseMessage包含本次工具所有的结果
         results = toolResponseMessage.getResponses().stream()
                .map(toolResponse -> "当前工具："+toolResponse.name()+",调用结果："+toolResponse.responseData())
                .collect(Collectors.joining("\n"));
         log.info(results);
         boolean terminate = toolResponseMessage.getResponses().stream()
                 .anyMatch(toolResponse -> toolResponse.name().equals("doTerminate"));
         if(terminate){
             this.setState(AgentState.FINISHED);
         }
        }catch (Exception e){
            log.error("工具执行失败："+e.getMessage());
            //getMessageList().add(new AssistantMessage("工具调用失败"));
        }


        return toolChatResponse.getResult().getOutput().getText();
    }
}
