package com.kxh.aiagent.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.modelretry.ModelRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.kxh.aiagent.tools.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class InvestMultiAgentConfig {

    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @Bean
    public ToolCallback[] dataCollectorTools() {
        WebSearchTool webSearchTool = new WebSearchTool();
        WebPageScape webPageScape = new WebPageScape();
        return ToolCallbacks.from(webSearchTool, webPageScape);
    }

    @Bean
    public ToolCallback[] reportTools() {
        MarkdownGenerationTool markdownGenerationTool = new MarkdownGenerationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        return ToolCallbacks.from(markdownGenerationTool, pdfGenerationTool);
    }

    @Bean
    public ReactAgent dataCollectorAgent(ChatModel dashscopeChatModel, ToolCallback[] dataCollectorTools) {
        return ReactAgent.builder()
                .name("DataCollectorAgent")
                .model(dashscopeChatModel)
                .tools(dataCollectorTools)
                .systemPrompt("""
                        你是一个专业的金融数据采集Agent。你的职责是：
                        1. 根据用户提供的股票代码或公司名称，搜索并收集相关的财务数据、市场信息和行业动态
                        2. 从互联网上获取最新的财报数据、估值指标、行业研报等信息
                        3. 整理并结构化收集到的数据，方便后续分析使用
                        注意：只收集数据，不做分析判断。确保数据来源可靠。
                        """)
                .instruction("""
                        使用搜索工具获取目标公司的：财务指标(营收、利润、ROE等)、估值数据(PE、PB、PS等)、
                        行业对比数据、最新公告和研报摘要。
                        将所有收集到的数据以结构化的方式返回。
                        """)
                .hooks(
                        ModelCallLimitHook.builder().runLimit(10).build(),
                        ToolCallLimitHook.builder().runLimit(8).build()
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
    public ReactAgent analysisAgent(ChatModel dashscopeChatModel) {
        return ReactAgent.builder()
                .name("AnalysisAgent")
                .model(dashscopeChatModel)
                .tools(new ToolCallback[0])
                .systemPrompt("""
                        你是一个资深的投资分析师Agent。你的职责是：
                        1. 基于收集到的财务数据和市场信息，进行深度分析
                        2. 评估公司的盈利能力、成长性、估值水平和风险因素
                        3. 与同行业公司进行对比分析
                        4. 给出明确的投资评级和目标价（如适用）
                        你的分析应该专业、客观、有据可依。
                        """)
                .instruction("""
                        对收集到的数据进行以下分析：
                        1. 基本面分析：盈利能力、偿债能力、运营效率
                        2. 估值分析：当前估值水平与历史和行业对比
                        3. 成长性分析：营收和利润增长趋势
                        4. 风险分析：主要风险因素和不确定性
                        5. 投资建议：明确的买入/持有/卖出建议及理由
                        """)
                .hooks(
                        ModelCallLimitHook.builder().runLimit(8).build()
                )
                .interceptors(
                        ModelRetryInterceptor.builder().maxAttempts(3).build(),
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }

    @Bean
    public ReactAgent reportAgent(ChatModel dashscopeChatModel, ToolCallback[] reportTools) {
        return ReactAgent.builder()
                .name("ReportAgent")
                .model(dashscopeChatModel)
                .tools(reportTools)
                .systemPrompt("""
                        你是一个专业的投资研报撰写Agent。你的职责是：
                        1. 将分析结果整合为一份完整、专业的投资分析报告
                        2. 报告需要结构清晰、逻辑严密、图文并茂
                        3. 使用Markdown格式生成最终报告文档
                        """)
                .instruction("""
                        根据分析结果，生成一份包含以下章节的投资分析报告（Markdown格式）：
                        1. 公司概况
                        2. 核心财务数据
                        3. 估值分析
                        4. 行业对比
                        5. 风险提示
                        6. 投资建议与目标价
                        使用generateMarkdown工具生成最终报告，并返回下载地址。
                        """)
                .hooks(
                        ModelCallLimitHook.builder().runLimit(8).build(),
                        ToolCallLimitHook.builder().runLimit(5).build()
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
    public SequentialAgent investSupervisorAgent(
            ReactAgent dataCollectorAgent,
            ReactAgent analysisAgent,
            ReactAgent reportAgent) {
        return SequentialAgent.builder()
                .name("InvestSequentialAgent")
                .description("投资分析流水线：数据采集 → 深度分析 → 报告生成")
                .subAgents(List.of(dataCollectorAgent, analysisAgent, reportAgent))
                .build();
    }
}
