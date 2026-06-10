package com.kxh.aiagent.ops.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.ProgressHook;
import com.kxh.aiagent.ops.tool.AliyunSlsTool;
import com.kxh.aiagent.ops.tool.EmailNotifyTool;
import com.kxh.aiagent.ops.tool.HistoryQueryTool;
import com.kxh.aiagent.ops.tool.LocalCodeTool;
import com.kxh.aiagent.ops.tool.LocalFileNotifyTool;
import com.kxh.aiagent.ops.tool.ProposeActionTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpsAgentConfig {

    // ==================== Stage 1: 告警解析 ====================

    @Bean
    public ReactAgent alertParserAgent(ChatModel dashscopeChatModel, ProgressEventBus progressEventBus) {
        return ReactAgent.builder()
                .name("AlertParserAgent")
                .model(dashscopeChatModel)
                .tools(new ToolCallback[0])
                .systemPrompt("""
                        你是阿里云 SLS 告警邮件解析专家。从输入的告警邮件中精准提取结构化信息。

                        阿里云 SLS 告警邮件典型规律：
                        - "appName=xxx" 后面跟着的是服务名(优先级最高)
                        - "告警严重度: 严重/高级/中级/低级"
                        - "告警内容: appName=xxx, 系统错误日志: [yyyy-MM-dd HH:mm:ss.SSS] ERROR,traceId,spanId,/path --- [thread] class.path : message  exception.stack"
                        - 告警内容里的方括号时间是真实错误发生时间(优先使用,不要用"告警首次触发时间")
                        - 紧跟 ERROR 之后第一个 32 位十六进制是 TraceId
                        - 异常类名通常含 "Exception" 或 "Error"
                        - 栈顶 3 帧形如 "at com.xxx.YYY.method(File.java:行号)"

                        必须以严格 JSON 输出，不要 markdown 代码块包裹，不要任何额外说明。""")
                .instruction("""
                        请输出一个严格的 JSON 对象，包含以下 7 个字段（缺失字段用 null）:

                        字段说明:
                        - service     字符串  服务名，优先取 appName= 后面的值
                        - errorClass  字符串  异常完整类名，如 java.lang.IndexOutOfBoundsException
                        - time        字符串  原始时间字符串
                        - timeSec     整数    Unix 秒级时间戳，基于"告警内容"里方括号时间换算(Asia/Shanghai 时区)
                        - topFrames   数组    栈顶 3 帧，每帧为字符串
                        - host        字符串  主机名或 null
                        - traceId     字符串  32 位十六进制或 null

                        关键要求:
                        1. timeSec 必须基于"告警内容"里的方括号时间换算
                        2. service 优先取 appName= 后面的值
                        3. 不要包含任何解释性文字，只输出 JSON 对象本身
                        4. 不要用 markdown 代码块包裹""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(2).build()
                )
                .interceptors(
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }

    // ==================== Stage 2: SLS 上下文扩展 ====================

    @Bean
    public ReactAgent logRetrievalAgent(ChatModel dashscopeChatModel,
                                         ProgressEventBus progressEventBus,
                                         AliyunSlsTool slsTool) {
        ToolCallback[] tools = ToolCallbacks.from(slsTool);
        return ReactAgent.builder()
                .name("LogRetrievalAgent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是 SLS 日志上下文扩展专家。
                        基于上游 AlertParserAgent 解析出的告警点信息,你必须按 5 种策略**全部调用** SLS 工具,
                        把"点信息"扩展为"面信息"。
                        5 个策略对应 5 个工具方法,每个都必须调用一次:
                        1. searchByTimeWindow(service, timeSec) - 时间窗扩展
                        2. searchByTraceId(traceId, timeSec) - 链路扩展 (若 traceId 为 null 则跳过)
                        3. searchByErrorClass(service, errorClass, timeSec) - 异常类扩展
                        4. searchByHost(host, timeSec) - 主机扩展
                        5. searchPre30sContext(service, host, timeSec) - 上下文扩展
                        每条日志的引用必须保留原始 time 和 host。""")
                .instruction("""
                        按 5 个策略调用 SLS 工具,然后输出一个严格 JSON 对象，包含以下 5 个数组字段:

                        - time_window       策略1返回的日志条目数组
                        - trace_link        策略2返回的日志条目数组
                        - error_class_trend 策略3返回的日志条目数组
                        - host_scope        策略4返回的日志条目数组
                        - pre_30s_context   策略5返回的日志条目数组

                        每个数组的每个日志条目保留 time / level / host / message / trace_id 五个字段。
                        不要 markdown 代码块包裹，不要解释性文字，只输出 JSON 对象本身。""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(8).build(),
                        ToolCallLimitHook.builder().runLimit(6).build()
                )
                .interceptors(
                        ToolErrorInterceptor.builder().build()
                )
                .outputKey("LogRetrievalAgent")
                .enableLogging(true)
                .build();
    }

    // ==================== Stage 2 (并行): 本地代码分析 ====================

    @Bean
    public ReactAgent codeAnalysisAgent(ChatModel dashscopeChatModel,
                                         ProgressEventBus progressEventBus,
                                         LocalCodeTool localCodeTool) {
        ToolCallback[] tools = ToolCallbacks.from(localCodeTool);
        return ReactAgent.builder()
                .name("CodeAnalysisAgent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是代码分析专家。基于 AlertParserAgent 提取的 service 和 topFrames(栈顶),
                        利用 LocalCodeTool 查阅本地源码,定位异常发生的代码上下文。

                        工作步骤(严格按序执行):
                        1) 先调 listAvailableServices 查看哪些服务有本地仓库配置
                        2) 若 service 已配置:
                           - 对栈顶 1-2 帧(最可能是业务代码的那帧)分别调用 readSourceByStackFrame
                             参数: service / 类全限定名 / 行号 / contextLines=15
                           - 必要时调 grepInService 找方法调用方
                        3) 若 service 未配置(返回 service_not_configured):
                           直接输出说明该服务未接入本地代码分析,不要尝试调用其他工具

                        识别"业务代码"还是"框架代码":
                        - JDK / Spring / Reactor / Hibernate 等是框架,跳过
                        - com.yiittou.* / com.example.* 等公司包名才是业务代码,重点分析

                        每段引用的代码必须保留文件相对路径和行号。""")
                .instruction("""
                        最终输出一个严格 JSON 对象:

                        - serviceConfigured  布尔   该服务是否有本地仓库配置
                        - analyzedFrames     数组   分析过的栈帧及代码
                          每项含: className / lineNo / file / codeSnippet(关键 5-10 行) / observation(2-3 句你的观察)
                        - suspiciousPoints   数组   你识别的可疑点
                          每项含: file / lineNo / reason
                        - recentChanges      数组   recentlyModifiedFiles 返回的相关文件(若调用了)
                        - summary            字符串 1-3 句总结代码层面的发现

                        不要 markdown 代码块包裹 JSON。如果 service 未配置,仅返回:
                        serviceConfigured=false 和 summary="该服务未接入本地代码分析,无法进行代码层定位"。""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(6).build(),
                        ToolCallLimitHook.builder().runLimit(5).build()
                )
                .interceptors(
                        ToolErrorInterceptor.builder().build()
                )
                .outputKey("CodeAnalysisAgent")
                .enableLogging(true)
                .build();
    }

    // ==================== Stage 2 (并行): 历史故障 RAG ====================

    @Bean
    public ReactAgent historyAgent(ChatModel dashscopeChatModel,
                                    ProgressEventBus progressEventBus,
                                    HistoryQueryTool historyQueryTool) {
        ToolCallback[] tools = ToolCallbacks.from(historyQueryTool);
        return ReactAgent.builder()
                .name("HistoryAgent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是历史故障检索专家。基于 AlertParserAgent 提取的 service + errorClass + summary,
                        利用 queryHistory 工具调用 Milvus 混合检索(BM25 + 向量),找出 Top3 历史相似事故。

                        工作步骤:
                        1) 构造查询文本: service + 异常类 + 简要描述 (50-100 字)
                        2) 调用 queryHistory(queryText, service, errorClass)
                        3) 整理返回结果

                        判断每条历史的相关性:
                        - score > 0.5 视为高相关
                        - 0.3-0.5 中相关
                        - < 0.3 弱相关,可丢弃""")
                .instruction("""
                        最终输出严格 JSON:

                        - serviceHasHistory  布尔   是否找到任何历史相似(score >= 0.3)
                        - hits               数组   每条含 score / serviceName / errorClass / summary / resolutionSummary(50-100字摘要)
                        - applicableInsight  字符串 1-3 句话总结这些历史能给当前事故什么启发(若无相关历史就写 "无相关历史可参考")

                        不要 markdown 代码块包裹。""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(3).build(),
                        ToolCallLimitHook.builder().runLimit(2).build()
                )
                .interceptors(ToolErrorInterceptor.builder().build())
                .outputKey("HistoryAgent")
                .enableLogging(true)
                .build();
    }

    // ==================== Stage 2 并行容器 ====================

    @Bean
    public ParallelAgent investigationParallel(
            ReactAgent logRetrievalAgent,
            ReactAgent codeAnalysisAgent,
            ReactAgent historyAgent) {
        return ParallelAgent.builder()
                .name("调查并行集群")
                .description("LogRetrieval(SLS) + CodeAnalysis(本地代码) + HistoryAgent(Milvus 混合检索) 同时跑")
                .subAgents(List.of(logRetrievalAgent, codeAnalysisAgent, historyAgent))
                .mergeStrategy(new ParallelAgent.ConcatenationMergeStrategy())
                .maxConcurrency(3)
                .build();
    }

    // ==================== Stage 3: 根因诊断 ====================

    @Bean
    public ReactAgent rootCauseAgent(ChatModel dashscopeChatModel,
                                      ProgressEventBus progressEventBus,
                                      LocalFileNotifyTool localFileTool,
                                      EmailNotifyTool emailTool,
                                      ProposeActionTool proposeActionTool) {
        ToolCallback[] tools = ToolCallbacks.from(localFileTool, emailTool, proposeActionTool);
        return ReactAgent.builder()
                .name("RootCauseAgent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是资深 SRE 根因分析专家 + HITL 决策提议者。
                        工作流程严格固定:
                        1) 阅读上游 4 个 Agent 的输出 (AlertParser / LogRetrieval / CodeAnalysis / HistoryAgent)
                        2) 内部推理 2-3 个根因假设
                        3) 调 notifyLocalFile 落本地 Markdown 报告
                        4) **调 proposeAction 产出 1 个 HITL 建议动作 (必做!不调=任务未完成)**

                        根因证据规则:
                        - 每个假设必须引用至少一条证据,优先级: 代码层 > 日志层 > 历史 RAG > 邮件正文
                        - confidence 自评 0.0-1.0; HistoryAgent 命中高相关历史时,优先复用其 resolution

                        HITL 动作选型(三选一):
                        - SUPPRESS_FINGERPRINT: 已知抖动 / 误报 / 临时不修 -> 屏蔽 N 小时;params {"hours": 4}
                        - NOTIFY_OWNER       : 严重影响业务 / 需上游介入 -> 升级邮件;params {"recipients":["sre@x.com"]}
                        - MARK_AS_KNOWN      : 根因明确 + resolution 清晰 -> 入历史库供后续 RAG 命中
                                                params {"errorClass":"xxx","resolution":"具体处置说明,100-300字"}
                        选不出来就保守用 NOTIFY_OWNER。""")
                .instruction("""
                        按以下顺序执行 (第 3 / 第 4 步都是必做工具调用):

                        STEP 1 (思考): 列出 2-3 个根因假设,每个含 description / confidence / evidences
                        STEP 2 (思考): 决定建议哪一个 HITL 动作 (SUPPRESS / NOTIFY / MARK_AS_KNOWN)
                        STEP 3 (调 notifyLocalFile):
                            title = [严重度] 服务名 - 简短根因
                            markdownContent = 完整 Markdown 报告
                        STEP 4 (调 proposeAction):
                            incidentId    = 输入里 "incidentId:" 字段的值 (原样照抄)
                            actionType    = 上面 STEP 2 选定的枚举字符串
                            target        = 简短目标摘要 (fingerprint 简写 / 收件人列表 / errorClass)
                            paramsJson    = 严格 JSON 字符串,字段见 systemPrompt
                            rationale     = 200-500 字, 解释为什么建议这个动作 (引用证据)

                        Markdown 报告结构建议:
                        ## 根因假设
                        ### 1. [描述]  置信度: XX%
                        - 证据: ...
                        ## 建议动作 (HITL)
                        - actionType: XXX
                        - 理由: ...

                        终止条件: 必须既调过 notifyLocalFile 又调过 proposeAction 才算完成。""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(5).build(),
                        ToolCallLimitHook.builder().runLimit(3).build()
                )
                .interceptors(
                        ToolErrorInterceptor.builder().build()
                )
                .enableLogging(true)
                .build();
    }

    // ==================== 顶层编排：诊断流水线 ====================

    @Bean
    public SequentialAgent opsIncidentPipeline(
            ReactAgent alertParserAgent,
            ParallelAgent investigationParallel,
            ReactAgent rootCauseAgent,
            BaseCheckpointSaver opsCheckpointSaver) {

        return SequentialAgent.builder()
                .name("OPS事故诊断流水线")
                .description("AlertParser → 并行(LogRetrieval SLS + CodeAnalysis 本地代码) → RootCause + 通知")
                .subAgents(List.of(
                        alertParserAgent,
                        investigationParallel,
                        rootCauseAgent
                ))
                .saver(opsCheckpointSaver)
                .build();
    }
}
