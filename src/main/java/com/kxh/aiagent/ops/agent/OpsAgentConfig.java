package com.kxh.aiagent.ops.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.kxh.aiagent.agent.progress.ProgressEventBus;
import com.kxh.aiagent.agent.progress.ProgressHook;
import com.kxh.aiagent.ops.tool.AliyunSlsTool;
import com.kxh.aiagent.ops.tool.DingTalkNotifyTool;
import com.kxh.aiagent.ops.tool.LocalCodeTool;
import com.kxh.aiagent.ops.tool.LocalFileNotifyTool;
import com.kxh.aiagent.ops.tool.WechatWorkNotifyTool;
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

    // ==================== Stage 2 并行容器 ====================

    @Bean
    public ParallelAgent investigationParallel(
            ReactAgent logRetrievalAgent,
            ReactAgent codeAnalysisAgent) {
        return ParallelAgent.builder()
                .name("调查并行集群")
                .description("LogRetrieval(SLS) + CodeAnalysis(本地代码) 同时跑")
                .subAgents(List.of(logRetrievalAgent, codeAnalysisAgent))
                .mergeStrategy(new ParallelAgent.ConcatenationMergeStrategy())
                .maxConcurrency(2)
                .build();
    }

    // ==================== Stage 3: 根因诊断 ====================

    @Bean
    public ReactAgent rootCauseAgent(ChatModel dashscopeChatModel,
                                      ProgressEventBus progressEventBus,
                                      LocalFileNotifyTool localFileTool,
                                      WechatWorkNotifyTool wechatWorkTool,
                                      DingTalkNotifyTool dingTalkTool) {
        ToolCallback[] tools = ToolCallbacks.from(localFileTool, wechatWorkTool, dingTalkTool);
        return ReactAgent.builder()
                .name("RootCauseAgent")
                .model(dashscopeChatModel)
                .tools(tools)
                .systemPrompt("""
                        你是资深 SRE 根因分析专家。
                        你的工作流程严格固定:
                        1) 阅读上游 3 个 Agent 的输出:
                           - AlertParserAgent     : 告警结构化字段
                           - LogRetrievalAgent    : SLS 日志上下文 (5 策略)
                           - CodeAnalysisAgent    : 本地代码层定位
                        2) 在内部推理出根因假设列表
                        3) 把诊断结果写成 Markdown 报告
                        4) **通过调用 notifyLocalFile 工具来完成你的任务**

                        关键规则:
                        - 你不能直接以聊天文本形式返回 JSON 或 Markdown
                        - 你的最终响应必须是工具调用的返回值
                        - 不调用工具 = 任务未完成
                        - 每个根因假设必须引用至少一条证据
                        - 证据 source 可选: alert-body / time_window / trace_link / error_class_trend / host_scope / pre_30s_context / code-snippet
                        - confidence 必须自评 0.0-1.0
                        - 优先级: 代码层证据 > 日志层证据 > 邮件正文""")
                .instruction("""
                        现在按以下步骤执行(注意第三步是工具调用,不是文本回复):

                        STEP 1 (内部思考): 列出 2-3 个根因假设,每个含 description / confidence / evidences
                        STEP 2 (内部思考): 选最高 confidence 作为 topConfidence,写 suggestedAction
                        STEP 3 (必做工具调用): 调用 notifyLocalFile,参数:
                            title = [严重度] 服务名 - 简短根因
                            markdownContent = 完整 Markdown 报告(含假设列表/置信度/证据/建议动作)
                        STEP 4 (可选工具调用): 若用户群有 webhook 可同时调用 notifyWechatWork

                        Markdown 报告的建议结构:
                        ## 根因假设
                        ### 1. [描述]  置信度: XX%
                        - 证据: ...
                        ### 2. ...
                        ## 建议动作
                        1. ...
                        2. ...

                        重要: 不要在对话中直接输出 JSON 文本。
                        你的最终响应必须是 notifyLocalFile 工具调用后返回的文件路径字符串。""")
                .hooks(
                        new ProgressHook(progressEventBus),
                        ModelCallLimitHook.builder().runLimit(4).build(),
                        ToolCallLimitHook.builder().runLimit(2).build()
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
            ReactAgent rootCauseAgent) {

        return SequentialAgent.builder()
                .name("OPS事故诊断流水线")
                .description("AlertParser → 并行(LogRetrieval SLS + CodeAnalysis 本地代码) → RootCause + 通知")
                .subAgents(List.of(
                        alertParserAgent,
                        investigationParallel,
                        rootCauseAgent
                ))
                .saver(MemorySaver.builder().build())
                .build();
    }
}
