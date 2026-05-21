# Agent 迁移计划：自研框架 → 官方 ReactAgent + 多Agent架构

## 一、现状分析

### 当前架构

```
BeasAgent (状态机 + SSE流式 + 死循环检测)
  └── ReActAgent (think → act 循环)
        └── ToolCallAgent (LLM推理 + ToolCallingManager执行)
              ├── KxhManus (通用Agent, @Component单例)
              └── InvestManus (投资Agent, @Component单例)
```

### 核心问题

| 问题 | 影响 | 官方方案 |
|------|------|----------|
| @Component 单例 + 共享 state/messageList | 并发请求互相污染 | threadId 隔离，每次 call 独立上下文 |
| 无重试/降级机制 | 工具调用失败直接中断 | ModelRetryInterceptor, ToolRetryInterceptor, ModelFallbackInterceptor |
| 死循环检测仅靠消息重复 | 无法限制模型调用次数 | ModelCallLimitHook, ToolCallLimitHook |
| 无上下文管理 | 长对话 token 溢出 | ContextEditingInterceptor, SummarizationHook |
| 无人工介入机制 | 高风险操作无法拦截 | HumanInTheLoopHook, InterruptionHook |
| 单 Agent 架构 | 复杂任务无法分工协作 | SequentialAgent, ParallelAgent, SupervisorAgent |

---

## 二、目标架构：企业级投资分析多Agent系统

```
                         ┌─────────────────────────┐
                         │   SupervisorAgent        │
                         │   (投资分析总调度)        │
                         └────────────┬────────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
    ┌─────────▼─────────┐  ┌─────────▼─────────┐  ┌─────────▼─────────┐
    │  DataCollector     │  │  AnalysisAgent     │  │  ReportAgent       │
    │  (数据采集Agent)    │  │  (分析研判Agent)    │  │  (报告生成Agent)    │
    │  - 财务数据抓取     │  │  - 估值分析         │  │  - Markdown报告     │
    │  - 行情数据获取     │  │  - 趋势研判         │  │  - 图表可视化       │
    │  - 公告/新闻爬取   │  │  - 风险评估         │  │  - PDF导出          │
    └───────────────────┘  └───────────────────┘  └───────────────────┘
```

### 设计原则

1. **单一职责** — 每个 Agent 专注一个领域，工具集精简
2. **可组合** — 通过 Flow Agent 编排，灵活组合
3. **可观测** — Hooks 提供执行过程可见性
4. **容错** — Interceptors 提供重试、降级、错误处理
5. **安全** — HumanInTheLoop 拦截高风险操作（如大额交易建议）

---

## 三、迁移步骤

### Phase 1：基础迁移 — 用 ReactAgent 替代 KxhManus

**目标**：验证 ReactAgent 可用性，保持现有功能不变

```java
@Configuration
public class AgentConfig {

    @Bean
    public ReactAgent kxhAgent(ChatModel dashscopeChatModel, 
                               ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("kxhAgent")
                .model(dashscopeChatModel)
                .tools(toolCallbacks)
                .systemPrompt("You are OpenManus, an all-capable AI assistant...")
                .hooks(List.of(
                    new ModelCallLimitHook(15),
                    new ToolCallLimitHook(30)
                ))
                .interceptors(List.of(
                    new ModelRetryInterceptor(3),
                    new ToolRetryInterceptor(2),
                    new ToolErrorInterceptor()
                ))
                .build();
    }
}
```

**关键变化**：
- 不再是 @Component 单例，而是通过 @Bean 创建
- 并发安全：每次调用传入不同 threadId
- 自动重试：模型调用失败重试3次，工具调用失败重试2次
- 步数限制：ModelCallLimitHook 替代手动 maxStep

**Controller 改造**：
```java
@PostMapping("/chat")
public String chat(@RequestParam String message, @RequestParam String threadId) {
    RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)  // 线程隔离
            .build();
    return kxhAgent.call(message, config);
}
```

### Phase 2：投资分析 Agent 拆分

**目标**：将 InvestManus 拆分为专业子 Agent

#### 2.1 DataCollectorAgent — 数据采集

```java
@Bean
public ReactAgent dataCollectorAgent(ChatModel model) {
    return ReactAgent.builder()
            .name("DataCollector")
            .model(model)
            .tools(financeDataTools())  // 财务数据、行情、公告
            .systemPrompt("你是数据采集专家，负责获取金融数据...")
            .interceptors(List.of(
                new ToolRetryInterceptor(3),
                new ToolErrorInterceptor()
            ))
            .build();
}
```

#### 2.2 AnalysisAgent — 分析研判

```java
@Bean
public ReactAgent analysisAgent(ChatModel model) {
    return ReactAgent.builder()
            .name("AnalysisAgent")
            .model(model)
            .tools(analysisTools())  // 估值计算、趋势分析
            .systemPrompt("你是投资分析专家，擅长财务分析和估值...")
            .interceptors(List.of(
                new ModelRetryInterceptor(2),
                new ContextEditingInterceptor()  // 防止长分析溢出
            ))
            .hooks(List.of(
                new SummarizationHook()  // 自动摘要长上下文
            ))
            .build();
}
```

#### 2.3 ReportAgent — 报告生成

```java
@Bean
public ReactAgent reportAgent(ChatModel model) {
    return ReactAgent.builder()
            .name("ReportAgent")
            .model(model)
            .tools(reportTools())  // Markdown生成、PDF导出
            .systemPrompt("你是投资报告撰写专家...")
            .build();
}
```

### Phase 3：多Agent编排

**目标**：用 SupervisorAgent 实现智能调度

```java
@Bean
public SupervisorAgent investmentSupervisor(
        ReactAgent dataCollectorAgent,
        ReactAgent analysisAgent,
        ReactAgent reportAgent,
        ChatModel model) {
    return SupervisorAgent.builder()
            .name("InvestmentSupervisor")
            .model(model)
            .agents(List.of(dataCollectorAgent, analysisAgent, reportAgent))
            .systemPrompt("""
                你是投资分析系统的总调度。根据用户需求：
                1. 如果需要数据，派发给 DataCollector
                2. 如果需要分析，派发给 AnalysisAgent
                3. 如果需要报告，派发给 ReportAgent
                对于完整的投资分析请求，按顺序调度所有Agent
                """)
            .hooks(List.of(
                new HumanInTheLoopHook(
                    "涉及具体投资建议时需要人工确认"
                ),
                new ModelCallLimitHook(50)
            ))
            .build();
}
```

**备选编排方式**：

```java
// 方式B：固定流水线（适合标准化报告）
@Bean
public SequentialAgent investmentPipeline(...) {
    return SequentialAgent.builder()
            .name("InvestmentPipeline")
            .agents(List.of(dataCollectorAgent, analysisAgent, reportAgent))
            .build();
}

// 方式C：智能路由（适合多样化查询）
@Bean  
public LlmRoutingAgent investmentRouter(...) {
    return LlmRoutingAgent.builder()
            .name("InvestmentRouter")
            .model(model)
            .agents(List.of(dataCollectorAgent, analysisAgent, reportAgent))
            .build();
}
```

### Phase 4：SSE 流式输出适配

**目标**：保持前端 SSE 体验

ReactAgent 的 `call()` 是同步的。流式输出方案：

```java
@GetMapping(value = "/invest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter investStream(@RequestParam String query, @RequestParam String threadId) {
    SseEmitter emitter = new SseEmitter(300_000L);
    
    CompletableFuture.runAsync(() -> {
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            // Hook 可以在每步执行后回调
            String result = investmentSupervisor.call(query, config);
            emitter.send(SseEmitter.event().data(result));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    
    return emitter;
}
```

### Phase 5：清理旧代码

确认新 Agent 稳定后：
- 删除 `BeasAgent.java`, `ReActAgent.java`, `ToolCallAgent.java`
- 删除 `KxhManus.java`, `InvestManus.java`
- 删除 `AgentState.java` 枚举
- 删除相关 Controller 中的旧 Agent 引用

---

## 四、Hooks & Interceptors 选型

### 推荐配置

| 组件 | 用途 | 适用场景 |
|------|------|----------|
| `ModelCallLimitHook(15)` | 防止无限循环 | 所有 Agent |
| `ToolCallLimitHook(30)` | 限制工具调用次数 | 数据采集 Agent |
| `HumanInTheLoopHook` | 高风险操作拦截 | Supervisor |
| `SummarizationHook` | 长上下文自动摘要 | 分析 Agent |
| `ModelRetryInterceptor(3)` | 模型调用重试 | 所有 Agent |
| `ToolRetryInterceptor(2)` | 工具调用重试 | 数据采集 Agent |
| `ModelFallbackInterceptor` | 模型降级 | 所有 Agent（主模型→备用模型）|
| `ToolErrorInterceptor` | 工具错误优雅处理 | 所有 Agent |
| `ContextEditingInterceptor` | 上下文裁剪 | 分析 Agent |

---

## 五、迁移时间线

| 阶段 | 内容 | 预计工作量 |
|------|------|-----------|
| Phase 1 | ReactAgent 替代 KxhManus | 1-2 天 |
| Phase 2 | 拆分投资分析子 Agent | 2-3 天 |
| Phase 3 | SupervisorAgent 编排 | 1-2 天 |
| Phase 4 | SSE 流式适配 | 1 天 |
| Phase 5 | 清理旧代码 | 0.5 天 |

---

## 六、风险与注意事项

1. **ReactAgent 内部状态管理** — 官方通过 `Saver` 接口持久化对话状态，需要实现 Redis 版 Saver 对接现有 Redis 消息持久化
2. **工具兼容性** — 现有 ToolCallback[] 可直接传入 ReactAgent.builder().tools()，无需改造
3. **DashScope ChatOptions** — ReactAgent 通过 `.chatOptions()` 设置，确保 `internalToolExecutionEnabled=false`
4. **并行工具执行** — ReactAgent 支持 `.parallelToolExecution(true)`，可加速数据采集
5. **A2A 远程 Agent** — 未来可通过 `A2aRemoteAgent` 将子 Agent 部署为独立服务，实现微服务化
