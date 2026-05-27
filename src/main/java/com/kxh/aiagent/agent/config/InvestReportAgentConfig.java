package com.kxh.aiagent.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.ProgressHook;
import com.kxh.aiagent.tools.MarkdownGenerationTool;
import com.kxh.aiagent.tools.PDFGenerationTool;
import com.kxh.aiagent.tools.finance.RemoteFinanceDataReader;
import com.kxh.aiagent.tools.finance.RemoteIndustryReader;
import com.kxh.aiagent.tools.finance.RemoteValuationReader;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class InvestReportAgentConfig {

    // ==================== Step 1: 数据采集 ====================

    @Bean
    public ReactAgent dataCollectorAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        RemoteFinanceDataReader financeReader = new RemoteFinanceDataReader();
        RemoteValuationReader valuationReader = new RemoteValuationReader();
        RemoteIndustryReader industryReader = new RemoteIndustryReader();
        ToolCallback[] tools = ToolCallbacks.from(financeReader, valuationReader, industryReader);

        return ReactAgent.builder()
                .name("数据采集Agent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是金融数据采集专家。你有3个工具可获取A股真实数据：
                        - readFinanceData: 财务三表(利润表/资产负债表/现金流量表) + 核心指标(ROE/ROA/毛利率等)
                        - readValuationData: PE-TTM/PB/PS/市值/预测PE
                        - readStockInfo: 公司基本信息(主营业务/行业/上市日期等)
                        - readIndustryPeers: 同行业可比公司列表
                        所有数据来自A股官方披露，真实可靠。你的任务是采集完整数据，不做分析。""")
                .instruction("""
                        按以下顺序采集目标股票的全部数据：
                        1. readStockInfo → 获取公司基本信息
                        2. readFinanceData(years=5) → 近5年财务数据
                        3. readValuationData(years=5) → 近5年估值数据
                        4. readIndustryPeers → 同行业对比
                        将所有数据以清晰的结构化格式输出，确保每个数据点标注了准确的年份。""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(10).build(),
                        ToolCallLimitHook.builder().runLimit(8).build()
                )
                .interceptors(
                        ToolRetryInterceptor.builder().maxRetries(2).build(),
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }

    // ==================== Step 2: 8 个并行分析 Agent ====================

    private ReactAgent buildAnalysisAgent(ChatModel model, ProgressEventBus progressEventBus,
                                          String name, String systemPrompt, String instruction) {
        return ReactAgent.builder()
                .name(name)
                .model(model)
                .tools(new ToolCallback[0])
                .systemPrompt(systemPrompt)
                .instruction(instruction)
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(6).build()
                )
                .interceptors(
                        ToolErrorInterceptor.builder().build()
                )
                .outputKey(name)
                .enableLogging(true)
                .build();
    }

    @Bean
    public ReactAgent businessModelAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "商业模式Agent", """
                你是商业模式分析专家。根据采集到的公司基本信息(主营业务、行业地位、收入构成)，
                分析该公司的商业模式特征、护城河、竞争壁垒和收入来源结构。
                输出一段200-300字的商业模式分析结论。""", """
                分析以下维度：
                1. 主营业务及收入构成（占比）
                2. 竞争优势/护城河（品牌、技术、规模、网络效应等）
                3. 行业地位（市场份额、定价权）
                4. 商业模式可持续性评估
                请引用数据中的具体数字来支撑你的结论。""");
    }

    @Bean
    public ReactAgent industryAnalysisAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "行业分析Agent", """
                你是行业研究分析师。根据公司的行业分类和同行业可比公司数据，
                分析行业景气度、竞争格局和发展趋势。
                输出一段200-300字的行业分析结论。""", """
                分析以下维度：
                1. 行业生命周期阶段（成长/成熟/衰退）
                2. 行业集中度与竞争格局
                3. 宏观经济和政策对行业的影响
                4. 公司在行业中的相对位置
                请引用数据中的具体信息来支撑你的结论。""");
    }

    @Bean
    public ReactAgent dupontAnalysisAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "杜邦分析Agent", """
                你是财务分析专家，擅长杜邦分析体系。根据ROE和财务三表数据，
                对ROE进行杜邦分解：ROE = 净利率 × 资产周转率 × 权益乘数。
                分析ROE变动的核心驱动因素。输出200-300字结论。""", """
                        ROE杜邦分解分析：
                        1. 列出近5年ROE变化趋势
                        2. 分解ROE为三个因子：净利率(=净利润/营收)、资产周转率(=营收/总资产)、权益乘数(=总资产/净资产)
                        3. 判断ROE变动主要由哪个因子驱动
                        4. 评估ROE的可持续性（高ROE是否依赖高杠杆？）
                        请从数据中提取具体数字进行计算。""");
    }

    @Bean
    public ReactAgent profitabilityAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "盈利能力Agent", """
                你是盈利能力分析专家。根据利润表数据，分析公司的盈利能力和盈利质量。
                关注毛利率、净利率、费用率趋势。
                输出200-300字盈利能力分析结论。""", """
                分析以下维度：
                1. 近5年毛利率趋势及变动原因
                2. 近5年净利率趋势
                3. 三费(销售/管理/财务)占营收比例及趋势
                4. 非经常性损益对利润的影响
                5. 盈利质量评估（利润含金量）
                请用具体数字支撑每个判断。""");
    }

    @Bean
    public ReactAgent growthAnalysisAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "成长性分析Agent", """
                你是成长性分析专家。根据近5年营收和利润数据，评估公司的成长性和增长驱动力。
                计算CAGR(复合年增长率)并判断增长质量。
                输出200-300字成长性分析结论。""", """
                分析以下维度：
                1. 近5年营收CAGR及增速趋势（加速/减速/稳定）
                2. 近5年净利润CAGR及与营收增速对比
                3. 增长驱动因素（内生增长 vs 外延并购）
                4. 未来增长可持续性判断
                请计算具体的增长率数据。""");
    }

    @Bean
    public ReactAgent cashflowQualityAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "现金流分析Agent", """
                你是现金流分析专家。根据现金流量表数据，评估公司的现金流质量和资金状况。
                关注经营现金流与净利润的匹配度、自由现金流水平。
                输出200-300字现金流分析结论。""", """
                分析以下维度：
                1. 经营现金流/净利润比值（含金量，>1为健康）
                2. 自由现金流水平及趋势
                3. 投资现金流方向（扩张/收缩）
                4. 筹资现金流反映的资金需求
                5. 现金流风险预警
                请从现金流量表提取具体数字计算。""");
    }

    @Bean
    public ReactAgent valuationAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "估值分析Agent", """
                你是估值分析专家。根据PE/PB/PS等估值指标和行业对比数据，
                判断公司当前估值水平是否合理。
                输出200-300字估值分析结论。""", """
                分析以下维度：
                1. 当前PE-TTM、PB与近5年历史分位对比
                2. 当前估值与同行业可比公司对比
                3. PEG评估（PE/增长率）
                4. 估值合理区间判断（低估/合理/高估）
                5. 如果数据中有预测PE，评估前瞻估值
                请用数据中的具体数字支撑判断。""");
    }

    @Bean
    public ReactAgent riskIdentificationAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "风险识别Agent", """
                你是风险分析专家。综合财务数据和行业信息，全面识别公司面临的风险因素。
                包括财务风险、经营风险、行业风险和宏观风险。
                输出200-300字风险评估结论。""", """
                从以下维度识别风险：
                1. 财务风险：负债率、流动比率、偿债能力
                2. 经营风险：客户集中度、原材料价格、竞争压力
                3. 行业风险：政策监管、技术变革、周期风险
                4. 宏观风险：利率、汇率、经济周期敏感度
                请给出每类风险的严重程度评估（高/中/低）。""");
    }

    // ==================== Step 2 并行容器 ====================

    @Bean
    public ParallelAgent analysisParallelAgent(
            ReactAgent businessModelAgent,
            ReactAgent industryAnalysisAgent,
            ReactAgent dupontAnalysisAgent,
            ReactAgent profitabilityAgent,
            ReactAgent growthAnalysisAgent,
            ReactAgent cashflowQualityAgent,
            ReactAgent valuationAgent,
            ReactAgent riskIdentificationAgent) {

        return ParallelAgent.builder()
                .name("并行分析集群")
                .description("8个分析Agent同时运行：商业模式/行业/杜邦/盈利/成长/现金流/估值/风险")
                .subAgents(List.of(
                        businessModelAgent,
                        industryAnalysisAgent,
                        dupontAnalysisAgent,
                        profitabilityAgent,
                        growthAnalysisAgent,
                        cashflowQualityAgent,
                        valuationAgent,
                        riskIdentificationAgent
                ))
                .mergeStrategy(new ParallelAgent.ConcatenationMergeStrategy())
                .maxConcurrency(8)
                .build();
    }

    // ==================== Step 3: 综合投资建议 ====================

    @Bean
    public ReactAgent investmentAdvisorAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return buildAnalysisAgent(dashscopeChatModel, progressEventBus, "投资建议Agent", """
                你是资深投资顾问。综合前面所有分析Agent的结论（商业模式、行业、杜邦、
                盈利、成长、现金流、估值、风险），给出最终投资建议。
                输出内容需包含：评级、目标价区间、核心逻辑。""", """
                综合以下8个维度的分析结论，给出最终投资建议：
                1. 投资评级（买入/增持/持有/减持/卖出）
                2. 目标价区间及依据
                3. 核心投资逻辑（3-5条要点）
                4. 主要风险提示（2-3条）
                请确保评级与各维度分析结论逻辑一致。
                如果多数维度积极，应给出买入/增持；如果多数维度消极，应给出减持/卖出。""");
    }

    // ==================== Step 4: 研报生成 ====================

    @Bean
    public ReactAgent reportAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        MarkdownGenerationTool markdownTool = new MarkdownGenerationTool();
        PDFGenerationTool pdfTool = new PDFGenerationTool();
        ToolCallback[] tools = ToolCallbacks.from(markdownTool, pdfTool);

        return ReactAgent.builder()
                .name("研报生成Agent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是专业研报撰写专家。你需要将数据采集结果、8个维度的分析结论、
                        以及投资建议整合为一份完整的markdown格式投资研报。
                        使用generateMarkdown工具生成最终研报文件。""")
                .instruction("""
                        生成一份包含以下章节的投资研报：
                        # 【股票名称】(股票代码) 投资分析报告

                        ## 一、公司概况
                        （来自商业模式和行业分析）

                        ## 二、财务分析
                        ### 2.1 盈利能力（含杜邦分析）
                        ### 2.2 成长性分析
                        ### 2.3 现金流质量

                        ## 三、估值分析
                        （历史分位 + 行业对比）

                        ## 四、风险提示

                        ## 五、投资建议与评级
                        （评级 + 目标价 + 核心逻辑）

                        报告生成后调用generateMarkdown工具，
                        文件名格式为：【股票名称】投资分析报告，
                        markdownContent为完整报告内容。
                        最后一定要返回Markdown文件的http下载地址！""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(8).build(),
                        ToolCallLimitHook.builder().runLimit(5).build()
                )
                .interceptors(
                        ToolRetryInterceptor.builder().maxRetries(2).build(),
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }

    // ==================== 顶层编排：串行流水线 ====================

    @Bean
    public SequentialAgent investReportPipeline(
            ReactAgent dataCollectorAgent,
            ParallelAgent analysisParallelAgent,
            ReactAgent investmentAdvisorAgent,
            ReactAgent reportAgent) {

        return SequentialAgent.builder()
                .name("投资研报流水线")
                .description("数据采集 → 8Agent并行分析 → 综合评级 → 研报生成")
                .subAgents(List.of(
                        dataCollectorAgent,
                        analysisParallelAgent,
                        investmentAdvisorAgent,
                        reportAgent
                ))
                .build();
    }
}
