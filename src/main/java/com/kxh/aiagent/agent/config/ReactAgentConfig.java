package com.kxh.aiagent.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.modelretry.ModelRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ReactAgentConfig {

    @Bean
    public ReactAgent generalAgent(ChatModel dashscopeChatModel, ToolCallback[] toolCallbacks2) {
        return ReactAgent.builder()
                .name("kxhAgent")
                .model(dashscopeChatModel)
                .tools(toolCallbacks2)
                .systemPrompt("""
                        You are OpenManus, an all-capable AI assistant,
                        aimed at solving any task presented by the user.
                        You have various tools at your disposal that you can call upon to efficiently
                        complete complex requests. 请返回你的思路，比如选择某个工具的原因。
                        """)
                .instruction("""
                        Based on user needs, proactively select the most appropriate tool or combination of tools.
                        For complex tasks, you can break down the problem and use different tools step by step.
                        After using each tool, clearly explain the execution results and suggest the next steps.
                        在你决定结束的同时整合会话历史，返回一个最终的回答结果。
                        """)
                .hooks(
                        ModelCallLimitHook.builder().runLimit(15).build(),
                        ToolCallLimitHook.builder().runLimit(10).build()
                )
                .interceptors(
                        ModelRetryInterceptor.builder().maxAttempts(3).build(),
                        ToolRetryInterceptor.builder().maxRetries(2).build(),
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }

    @Bean
    public ReactAgent investAgent(ChatModel dashscopeChatModel, ToolCallback[] toolCallbacks2) {
        return ReactAgent.builder()
                .name("InvestAgent")
                .model(dashscopeChatModel)
                .tools(toolCallbacks2)
                .systemPrompt("""
                        You are InvestGPT, a highly capable investment research AI assistant,
                        specialized in analyzing financial statements, valuation metrics,
                        market trends, and generating professional investment research reports.
                        You have access to various tools including:
                          - Financial Data Retrieval (historical and current financial statements)
                          - Valuation Metrics Retrieval (PE, PB, PS, etc.)
                          - Visualization Tools (charts, trend analysis)
                          - Markdown/PDF Report Generation
                        When responding to user requests:
                          - First, interpret the user's query and identify the data and tools needed.
                          - Produce outputs in a format suitable for inclusion in professional investment reports.
                        """)
                .instruction("""
                        Based on user needs, select the appropriate tool. For complex tasks,
                        break down the problem and use different tools step by step to solve it.
                        你选择一个工具同时能告诉我选择它的前因后果。
                        最后请用工具生成一份markdown文档，生成最终的投资分析及建议，请把markdown下载地址告诉我。
                        """)
                .hooks(
                        ModelCallLimitHook.builder().runLimit(20).build(),
                        ToolCallLimitHook.builder().runLimit(10).build()
                )
                .interceptors(
                        ModelRetryInterceptor.builder().maxAttempts(3).build(),
                        ToolRetryInterceptor.builder().maxRetries(2).build(),
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }
}
