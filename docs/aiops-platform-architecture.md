# AIOps 智能运维平台 — 架构设计文档

> 版本：v1.0
> 状态：设计中
> 作者：康新辉
> 最后更新：2026-06-03

---

## 1. 文档说明

本文档定义基于现有 Spring AI Alibaba 多智能体框架，扩展构建的 **AIOps 智能运维平台** 的整体架构。该平台并非单一场景实现，而是一个 **可插拔多源接入 + 统一调查诊断流水线** 的运维自动化基础设施。

### 1.1 范围

**包含**：
- 告警接入（邮件、Webhook、主动巡检、Metrics 触发）
- 异常调查（日志检索、代码分析、指标查询、链路追踪、历史知识库）
- 智能诊断（多 Agent 协同根因分析）
- 决策与执行（HITL 人工确认、白名单受控自动执行）
- 知识沉淀（故障复盘、向量化入库）

**不包含**：
- 监控数据采集本身（依赖现有 SLS / Prometheus / Druid 输出）
- 告警源 SLA 保障（不做告警源高可用，仅消费）
- CI/CD 流水线本身（仅触发外部 CI/CD 接口）

### 1.2 设计原则

1. **接入与处理解耦** — 新增告警源不影响下游 Agent 流水线
2. **工具按风险分级** — 只读 / 通知 / 执行 三级隔离
3. **默认安全** — 所有受控动作必经 HITL，白名单严格收敛
4. **可观测优先** — 每个 Agent 调用、每个工具调用、每个动作执行全部可追溯
5. **复用现有框架** — 最大化复用 SequentialAgent / ParallelAgent / ProgressHook / CheckPointer

---

## 2. 业务目标与场景

### 2.1 总体目标

将运维人员从重复性告警响应工作中解放出来。让 Agent 完成：
- 告警接收后第一时间自动调查
- 综合日志/代码/指标/历史经验给出根因假设
- 高置信场景下自动执行修复，低置信场景下推送人工
- 解决后自动沉淀知识，下次相同问题秒级响应

### 2.2 目标场景

#### 2.2.1 核心工作流（V1 主要交付目标）

**线上服务 ERROR → 邮件告警 → SLS 上下文扩展 → 诊断推送**

这是当前线上实际的人工工作流：
1. 线上服务抛 ERROR，监控系统发送邮件，邮件中带 **简略堆栈信息**（exception class + 几行栈顶 + 服务名 + 时间）
2. 运维/研发收到邮件后，**手动**打开阿里云 SLS 控制台
3. **手动**根据邮件里的服务名、时间、异常类去构造查询
4. **手动**翻阅日志上下文，找到根因

平台要替代的就是步骤 2-4：
- Agent 自动解析邮件提取关键信息
- Agent 自动构造 SLS 查询并**扩展上下文窗口**（不只查邮件中的那一行）
- Agent 综合上下文信息给出诊断假设
- 通过钉钉/飞书推送结果

#### 2.2.2 其他场景（后续扩展，按 Phase 3+ 接入）

| 场景 | 触发方式 | 期望效果 | 优先级 |
|------|---------|---------|--------|
| **邮件告警自动处理（V1 核心）** | EmailAlertSource | 收到 ERROR 邮件 5 秒内推送钉钉诊断报告 | **P0** |
| Druid 连接池监控 | DruidProbeSource（定时巡检） | 连接池近满自动诊断，定位慢 SQL | P1 |
| 线上服务异常突增扫描 | ServiceLogScanSource（定时拉 SLS） | ERROR 数突增自动调查 + 锁定可疑提交 | P1 |
| 链路调查（人工触发） | HTTP Webhook | 输入 TraceId 自动串联跨服务日志 | P2 |
| JVM 异常监控 | JvmMetricSource | OldGen 持续高位触发 GC 分析 | P2 |
| 历史故障检索 | 任意场景作为子流程 | 相似故障 RAG 命中直接出方案 | P2 |

---

## 3. 架构总览

### 3.1 分层架构图

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. 接入层 (Source Layer) — 可插拔                                  │
│ ┌─────────┬──────────┬───────────┬──────────────────────────┐   │
│ │ Email  │ Webhook │ Scheduled │ Metrics Threshold        │   │
│ │ IMAP   │ POST    │ Probes    │ Polling                  │   │
│ └─────────┴──────────┴───────────┴──────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. 归一化 + 去重聚合层                                              │
│ - RawAlert → IncidentEvent 统一模型                                │
│ - Redis 指纹去重 (service + signature)                             │
│ - 5min 滑窗聚合                                                    │
│ - 优先级评估 (P0 / P1 / P2)                                        │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. 路由层 (Router)                                                  │
│ - 规则库 (RuleEngine) 优先匹配                                      │
│   命中 → 直接出方案 (跳过 LLM)                                      │
│   未命中 → 进入 Agent 流水线                                        │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. 调查流水线 (复用 SequentialAgent + ParallelAgent)                 │
│                                                                  │
│  Stage 1: AlertParserAgent                                       │
│  Stage 2: 并行调查 (5 路)                                          │
│    ├─ LogRetrievalAgent   — Aliyun SLS 工具                      │
│    ├─ CodeAnalysisAgent   — Git log/diff/blame 工具              │
│    ├─ MetricsAgent        — Druid/JVM/Prometheus 工具            │
│    ├─ TraceAgent          — 链路追踪 (TraceId)                   │
│    └─ HistoryAgent        — Milvus RAG 历史故障                  │
│  Stage 3: RootCauseAgent  — 假设排序 + 置信度评估                  │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. 决策层 (DecisionEngine)                                          │
│ 置信度 > 90% + 白名单动作 → AUTO_EXECUTE                            │
│ 置信度 50-90%             → HITL_CONFIRM (钉钉交互按钮)             │
│ 置信度 < 50%              → MANUAL_ONLY (转人工)                   │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. 执行层 (ActionExecutor)                                          │
│ 通知动作: 钉钉/飞书/邮件                                            │
│ 受控动作: K8s重启 / 版本回滚 / 扩缩容 (严格白名单)                    │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. 复盘 + 知识沉淀                                                  │
│ - PostmortemAgent 自动生成复盘报告                                  │
│ - 解决方案向量化 → Milvus incident_knowledge collection            │
│ - 下次相似告警 Stage 2 HistoryAgent 直接 RAG 命中                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 8. 横切层 (Cross-cutting)                                          │
│ - ProgressHook  : 全链路可观测 + 耗时/Token 追踪                    │
│ - CheckPointer  : 流水线断点续跑 (Redis/Postgres Saver)             │
│ - AuditLogger   : 每个动作双重审计 (触发者 + 确认者)                  │
│ - InputValidator: 工具参数白名单校验                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 数据流时序（典型场景）

```
[告警源]──RawAlert──▶[Normalizer]──IncidentEvent──▶[Dedup]
                                                      │
                                  命中已知指纹 ──Yes─▶ 丢弃/合并计数
                                                      │
                                                      No
                                                      ▼
                                                  [Router]
                                                      │
                                         规则匹配 ──Yes─▶ 直接出方案
                                                      │
                                                      No
                                                      ▼
                                          [InvestigationPipeline]
                                                      │
                                            ┌─────────┼─────────┐
                                            ▼         ▼         ▼
                                          Log     Code      History
                                          Agent   Agent     Agent
                                            └─────────┼─────────┘
                                                      ▼
                                              [RootCauseAgent]
                                                      │
                                                      ▼
                                              [DecisionEngine]
                                            ┌─────────┼─────────┐
                                            ▼         ▼         ▼
                                          AUTO     HITL      MANUAL
                                            │         │         │
                                            └─────────┼─────────┘
                                                      ▼
                                              [ActionExecutor]
                                                      ▼
                                              [Postmortem]
                                                      ▼
                                              [Milvus 入库]
```

---

## 4. 核心抽象与数据模型

### 4.1 关键接口

```java
/**
 * 告警源统一抽象 — 新增源类型只需实现此接口
 */
public interface AlertSource {
    String sourceType();
    Flux<RawAlert> stream();
}

/**
 * 归一化器 — 不同源到统一事件模型
 */
public interface AlertNormalizer<T extends RawAlert> {
    IncidentEvent normalize(T rawAlert);
}

/**
 * 知识库统一抽象 — 屏蔽底层向量库实现
 * 默认实现 MilvusIncidentKnowledgeBase
 */
public interface IncidentKnowledgeBase {
    void save(ResolvedIncident incident);
    List<SimilarIncident> findSimilar(IncidentEvent event, int topK);
    void delete(String incidentId);
}

/**
 * 动作执行器 — 通知与受控动作的统一接口
 */
public interface ActionExecutor {
    String actionType();
    boolean canExecute(ActionRequest request);  // 白名单校验
    ActionResult execute(ActionRequest request);
}
```

### 4.2 核心数据模型

```java
/** 统一事故事件 */
public record IncidentEvent(
    String incidentId,         // UUID
    String fingerprint,        // hash(service + errorSignature) 用于去重
    String source,             // "email" / "druid" / "log_scan" / ...
    String serviceName,
    Severity severity,         // P0/P1/P2
    Instant occurredAt,
    Map<String, Object> raw,   // 原始数据保留
    String summary             // LLM 可读简述
) {}

/** 诊断报告 */
public record DiagnosisReport(
    String incidentId,
    List<Hypothesis> hypotheses,    // 按置信度排序的假设
    double topConfidence,            // 最高置信度
    String suggestedAction,          // 建议动作
    List<Evidence> evidences,        // 引用证据 (日志行/commit hash)
    long totalDurationMs,
    int totalTokens
) {}

/** 决策结果 */
public record Decision(
    String incidentId,
    DecisionMode mode,               // AUTO_EXECUTE / HITL_CONFIRM / MANUAL_ONLY
    ActionRequest action,
    String reasoning
) {}

/** 已解决事故 — 用于知识沉淀 */
public record ResolvedIncident(
    String incidentId,
    IncidentEvent originalEvent,
    DiagnosisReport diagnosis,
    String resolution,               // 实际解决方案
    String resolvedBy,
    Instant resolvedAt,
    boolean autoResolved             // 是否自动解决
) {}
```

---

## 5. 模块详细设计

### 5.1 接入层 (Source Layer)

#### 5.1.1 EmailAlertSource

基于 Spring Mail IMAP。轮询或 IDLE 模式监听指定收件箱。

```java
@Component
public class EmailAlertSource implements AlertSource {
    @Scheduled(fixedDelay = 30000)
    public void poll() {
        // 1. IMAP 拉取未读邮件
        // 2. 解析 Subject + Body
        // 3. 通过 EmailAlertNormalizer 转为 IncidentEvent
        // 4. 标记已读
    }
}
```

**配置**：
```yaml
ops:
  source:
    email:
      enabled: true
      host: imap.example.com
      port: 993
      username: ${EMAIL_USER}
      password: ${EMAIL_PASS}
      folder: INBOX
      subject-pattern: "\\[ERROR\\].*"
```

#### 5.1.2 WebhookAlertSource

```
POST /api/ops/alert/webhook
Body: { "source": "prometheus", "service": "...", "alert": "..." }
```

适用于 Prometheus AlertManager、Grafana、CI/CD 失败回调等推送场景。

#### 5.1.3 DruidProbeSource

```java
@Component
public class DruidProbeSource implements AlertSource {
    @Scheduled(fixedDelay = 60000)
    public void probe() {
        for (String service : configuredServices) {
            DruidStats stats = restTemplate.getForObject(
                "http://" + service + "/druid/index.json", DruidStats.class);
            if (stats.activeCount() > stats.maxActive() * 0.9) {
                publish(buildIncident(service, stats));
            }
        }
    }
}
```

**配置**：
```yaml
ops:
  source:
    druid:
      enabled: true
      services:
        - host: order-service.internal
          threshold: 0.9
        - host: payment-service.internal
          threshold: 0.85
```

#### 5.1.4 ServiceLogScanSource

定时查询 SLS 中 ERROR 日志增长率，突增触发告警。

```java
@Component
public class ServiceLogScanSource implements AlertSource {
    @Scheduled(fixedDelay = 300000)  // 5 min
    public void scan() {
        for (String service : configuredServices) {
            int currentErrors = slsTool.countErrors(service, 5);  // 近 5min
            int baseline = slsTool.countErrors(service, 60) / 12; // 1h 平均
            if (currentErrors > baseline * 3) {
                publish(buildIncident(service, currentErrors, baseline));
            }
        }
    }
}
```

#### 5.1.5 JvmMetricSource (未来)

通过 actuator `/actuator/metrics/jvm.memory.used` 拉取，老年代持续高位触发。

---

### 5.2 归一化与去重

#### 5.2.1 IncidentNormalizer

每个 AlertSource 配套一个 Normalizer 实现，统一产出 `IncidentEvent`。

#### 5.2.2 IncidentDeduplicator

```java
@Component
public class IncidentDeduplicator {
    @Autowired RedisTemplate redis;
    
    public boolean shouldProcess(IncidentEvent event) {
        String key = "ops:dedup:" + event.fingerprint();
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofMinutes(5));
        return count == 1;  // 仅首次触发流水线
    }
    
    public void recordOccurrence(String fingerprint) {
        // 累计 count，用于"同类告警 5min 内出现 N 次"统计
    }
}
```

**指纹算法**：
```
fingerprint = SHA256(serviceName + ":" + normalizedErrorSignature)
normalizedErrorSignature = 提取 stacktrace 顶部异常类名 + 去除变量化数字/UUID 后的 message
```

---

### 5.3 路由层

#### 5.3.1 RuleEngine

匹配已知模式直接出方案，跳过 LLM。

```yaml
ops:
  rules:
    - name: "JVM OOM"
      match:
        errorClass: "java.lang.OutOfMemoryError"
      action:
        type: "notify"
        message: "{service} 发生 OOM，建议检查堆 dump 和 GC 日志"
        runbook: "https://wiki.../oom-handling"
    - name: "Connection refused"
      match:
        messagePattern: ".*Connection refused.*"
      action:
        type: "notify"
        message: "{service} 连接被拒，检查下游服务状态"
```

命中率高的常见故障走规则，复杂未知故障走 Agent。

---

### 5.4 调查流水线 (Investigation Pipeline)

#### 5.4.0 核心模式：上下文扩展 (Context Expansion Pattern)

V1 流水线的核心行为模式是**从邮件的"点信息"扩展为 SLS 的"面信息"**：

```
                  ┌─────────────────────────────┐
邮件中的"点"信息 → │  Context Expansion via SLS  │ → 完整上下文
                  └─────────────────────────────┘

输入（点）：              扩展查询（面）：
- service: order-svc      ┌─ 同时间窗 (±5min) 该服务全部 ERROR
- time: 14:23:45      →   ├─ 同 TraceId 的跨服务全链路日志
- exception: NPE          ├─ 同异常类近 1h 出现次数与分布
- host: prod-order-01     ├─ 同 host 该时间段所有 WARN/ERROR
- traceId: abc123         └─ 异常前 30s 该服务 INFO（看上下文操作）
```

**这一模式由 `LogRetrievalAgent` 实施**，它不是单一的关键词搜索，而是按预定义的 **5 种扩展策略**自动构造多个 SLS 查询，把零散的"点"还原成可诊断的"面"。

具体扩展策略：

| 策略 | 查询条件 | 用途 |
|------|---------|------|
| 时间窗扩展 | `service:X AND level:ERROR` + `[t-5min, t+5min]` | 看是否多实例同时报错 |
| 链路扩展 | `trace_id:abc` + `[t-1min, t+1min]` | 看上下游链路调用情况 |
| 异常类扩展 | `exception:NullPointerException AND service:X` + `[t-1h, t]` | 看该异常是否近期突增 |
| 主机扩展 | `host:prod-order-01` + `[t-2min, t+2min]` | 看是否单机问题 |
| 上下文扩展 | `service:X AND host:prod-order-01` + `[t-30s, t]` | 看错误前发生了什么 |

每个策略都通过单独的 SLS 工具调用执行，Agent 把多路结果汇总为结构化输出。

#### 5.4.1 流水线结构

复用现有 `SequentialAgent + ParallelAgent` 编排：

```
SequentialAgent("opsIncidentPipeline")
├─ ReactAgent("AlertParserAgent")          # 解析邮件提取"点"
├─ ParallelAgent("investigationParallel")  # 并行多源调查
│  ├─ ReactAgent("LogRetrievalAgent")      # 【核心】5 策略 SLS 上下文扩展
│  ├─ ReactAgent("CodeAnalysisAgent")      # Git 提交分析（Phase 2+）
│  ├─ ReactAgent("MetricsAgent")           # 指标查询（Phase 3+）
│  ├─ ReactAgent("TraceAgent")             # 链路追踪（Phase 2+，与 LogAgent 部分重叠）
│  └─ ReactAgent("HistoryAgent")           # Milvus RAG（Phase 4+）
└─ ReactAgent("RootCauseAgent")            # 综合诊断
```

> **V1 必做**：AlertParserAgent + LogRetrievalAgent + RootCauseAgent（最小闭环）
> **Phase 2+ 加入**：CodeAnalysisAgent / TraceAgent
> **Phase 4+ 加入**：HistoryAgent

#### 5.4.2 各 Agent 职责

| Agent | 系统提示词关键点 | 输出契约 |
|-------|----------------|---------|
| AlertParserAgent | "你是告警邮件解析专家。从邮件正文中提取：服务名、发生时间(精确到秒)、异常类名、栈顶 3 帧、host、TraceId(如有)" | `{service, errorClass, time, topFrames, host, traceId}` |
| **LogRetrievalAgent** | "**你是 SLS 日志上下文扩展专家**。基于已解析的告警点信息，必须按 5 种策略分别调用 SLS 工具：时间窗扩展 / 链路扩展 / 异常类扩展 / 主机扩展 / 上下文扩展。每条引用必须带原始时间戳与日志行号。" | `{timeWindow: [...], traceLink: [...], errorClassTrend: [...], hostScope: [...], pre30sContext: [...]}` |
| CodeAnalysisAgent | "**主用 LocalCodeTool 读取栈顶涉及的源码**（fast filesystem），**辅以 GitRepoTool 查最近 24h 提交**（仅在怀疑回归时调用）" | `{sourceCode: {...}, suspiciousCommits: [...]}` |
| MetricsAgent | "查询服务指标（CPU/MEM/QPS/RT），观察趋势" | `{metrics: {...}, trend: "..."}` |
| TraceAgent | "若 LogRetrievalAgent 的链路扩展结果不完整，进一步深挖 TraceId" | `{traceFlow: [...], hotspot: "..."}` |
| HistoryAgent | "Milvus RAG 检索历史相似故障，返回 Top3 案例及解决方案" | `{similarCases: [...]}` |
| RootCauseAgent | "综合所有调查信息，输出假设排序与置信度。**每个假设必须引用至少一条 Evidence（日志行号+时间戳，或 commit hash）**。" | `{hypotheses: [...], confidence: 0.85}` |

#### 5.4.3 反幻觉机制

`RootCauseAgent` 系统提示词强制要求：
- 每个假设必须引用至少一条 Evidence（日志行号+时间戳，或 commit hash）
- 置信度必须自评（0.0-1.0），并说明为什么不是更高/更低
- 输出 JSON 结构化，由 schema 校验

---

### 5.5 工具层 (Tool Layer)

#### 5.5.1 工具分级

| 等级 | 类型 | 工具示例 | 安全要求 |
|------|------|---------|---------|
| **L1 只读** | 信息检索 | AliyunSlsTool, GitRepoTool, DruidMetricsTool, JvmMetricsTool, PrometheusQueryTool | AK/SK 子账号 + 参数校验 |
| **L2 通知** | 推消息 | DingTalkTool, FeishuTool, MailTool | 限流 (每分钟上限) |
| **L3 执行** | 改状态 | K8sRestartPodTool, DeployRollbackTool, ScaleReplicaTool | **白名单 + HITL + 审计** |

#### 5.5.2 关键工具设计

**AliyunSlsTool**：
```java
@Tool(description = "检索阿里云 SLS 日志")
public String searchLogs(
    @ToolParam String logstore,
    @ToolParam String query,
    @ToolParam long fromTs,
    @ToolParam long toTs,
    @ToolParam int limit
)
```
约束：
- logstore 必须在配置白名单内
- 时间窗 ≤ 24h
- limit ≤ 100
- 单条日志 message 截断至 500 字符

**代码分析双工具策略**：

#### LocalCodeTool（filesystem，主力）

性能优先，所有"查找和读取"操作走本地文件系统：

```java
@Tool public String findSymbol(String service, String className)
       // 在本地代码库中找类定义所在文件，ripgrep 实现
@Tool public String findMethod(String service, String methodName)
       // 找方法定义，regex 匹配
@Tool public String readSourceAround(String service, String file, int line, int contextLines)
       // 直接 Files.readAllLines，取指定行 ± N 行
@Tool public String grepInService(String service, String keyword)
       // 全文搜索关键词
@Tool public String listFiles(String service, String pattern)
       // glob 匹配列文件
```

**性能特征**：单次调用 < 50ms（无 fork 开销）。

**配置**：
```yaml
ops:
  code:
    repos:
      - name: order-service
        path: /opt/repos/order-service
        package-prefix: com.example.order
    sync:
      enabled: true
      interval: 10m        # 定时 git pull
      branch: main
```

**栈帧到本地文件的快速定位**：邮件中的 `OrderService.java:127` 通过 `package-prefix` 反推路径 → 拼接 `{repo.path}/src/main/java/{class-path}` → 直接定位文件读取，无需搜索。

#### GitRepoTool（git CLI，按需）

仅在需要**历史信息**时调用，性能较慢（100-500ms/次），不放进默认调查链：

```java
@Tool public String recentCommits(String service, int hours)
       // 列最近 N 小时提交
@Tool public String diffOfCommit(String service, String commitHash)
       // 查看具体改动
@Tool public String blameAtLine(String service, String file, int line)
       // 这行是谁什么时候改的
```

**约束（两个工具共享）**：
- service 必须命中白名单（防路径穿越如 `service=../../etc`）
- 单次最多读 200 行代码，单文件 ≤ 1MB
- 禁止访问 `.git/` `target/` `node_modules/` 等
- 禁止读二进制文件（按扩展名过滤）

**K8sRestartPodTool** (L3)：
```java
@Tool public String restartPod(String namespace, String deploymentName)
```
约束：
- namespace + deploymentName 必须双重命中白名单
- 必须 HITL 确认通过才能调用
- 调用后写 AuditLog
- 同一目标 1 小时内只能重启 3 次

---

### 5.6 决策层 (Decision Engine)

```java
@Component
public class DecisionEngine {
    
    public Decision decide(DiagnosisReport report) {
        double confidence = report.topConfidence();
        String action = report.suggestedAction();
        
        if (confidence > 0.9 && actionWhitelist.allowsAuto(action)) {
            return Decision.auto(report.incidentId(), action);
        }
        if (confidence > 0.5) {
            return Decision.hitl(report.incidentId(), action);
        }
        return Decision.manual(report.incidentId());
    }
}
```

#### 5.6.1 ActionWhitelist 配置

```yaml
ops:
  action:
    whitelist:
      auto:
        - type: "notify"
          targets: ["*"]                    # 通知动作允许所有目标
        - type: "scale_replica"
          targets: ["payment-svc/dev"]      # 仅 dev 环境支付服务允许自动扩容
      hitl:
        - type: "restart_pod"
          targets: ["*/prod"]               # 生产环境重启必须 HITL
        - type: "rollback"
          targets: ["*/*"]                  # 任何回滚必须 HITL
      forbidden:
        - type: "delete_*"                  # 删除类动作永远禁止自动
```

#### 5.6.2 HITL 交互流程

钉钉/飞书工作通知发送带按钮的诊断报告：

```
[告警] order-service 发生 NPE
═══════════════════════════════
诊断: 75% 置信度 - commit a1b2c3 引入空指针
证据: order.log:1428 / commit a1b2c3 line 67
建议动作: 回滚到 commit before a1b2c3

[确认执行] [修改方案] [转人工]
```

用户点击按钮 → 钉钉回调 → `HitlConfirmGateway` → 触发 `ActionExecutor`。

---

### 5.7 执行层

```java
@Component
public class ActionDispatcher {
    private final Map<String, ActionExecutor> executors;
    
    public ActionResult dispatch(ActionRequest request, String operator) {
        ActionExecutor executor = executors.get(request.type());
        if (!executor.canExecute(request)) {
            throw new ActionForbiddenException();
        }
        
        auditLogger.logTrigger(request, operator);
        ActionResult result = executor.execute(request);
        auditLogger.logResult(request, result);
        return result;
    }
}
```

---

### 5.8 知识库 (Milvus Vector Store)

#### 5.8.1 选型说明

向量库统一使用 **Milvus**：
- 项目 `application-milvus.yml` 已有基础配置
- 适合大规模相似度检索（>10w 历史故障）
- 支持元数据过滤（按 service 隔离查询）
- 与 Spring AI 集成度好

通过 `IncidentKnowledgeBase` 接口屏蔽底层实现，未来可切换至 PgVector / Elasticsearch。

#### 5.8.2 Collection 设计

**Collection 名称**: `incident_knowledge`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT64 (auto-id, primary) | 自动主键 |
| vector | FLOAT_VECTOR (dim=1536) | text-embedding-v2 嵌入 |
| incident_id | VARCHAR(36) | 关联事故 UUID |
| service_name | VARCHAR(64) | 服务名（分区键） |
| error_signature | VARCHAR(256) | 错误指纹（去变量后）|
| summary | VARCHAR(2000) | 事故简述 |
| resolution | TEXT | 解决方案完整内容 |
| severity | VARCHAR(8) | P0/P1/P2 |
| occurred_at | INT64 | 发生时间戳（毫秒）|
| resolved_at | INT64 | 解决时间戳 |
| auto_resolved | BOOL | 是否自动解决 |
| tags | VARCHAR(512) | JSON 字符串 (extra metadata) |

**索引**：
- 字段：`vector`
- 类型：`HNSW`
- 度量：`COSINE`
- 参数：`{ "M": 16, "efConstruction": 200 }`

**分区策略**：
- 按 `service_name` 分区（partition_key）
- 大幅提升按服务过滤的检索性能
- 避免一个高频服务的故障数据淹没其他服务

#### 5.8.3 嵌入策略

入库时拼接：
```
[Service] {service_name}
[Error] {error_signature}
[Summary] {summary}
[Resolution] {resolution}
```
作为整段文本嵌入。

检索时构造查询：
```
[Service] {current_service}
[Error] {current_error_signature}
[Summary] {current_summary}
```

#### 5.8.4 检索参数

- `topK = 5`
- `searchParams = { "ef": 64 }`
- 相似度阈值：`> 0.75` 才返回
- 时间衰减：超过 180 天的结果权重 × 0.5（避免旧方案误导）

---

### 5.9 横切层

#### 5.9.1 ProgressHook 扩展

现有 ProgressHook 已支持耗时/Token 追踪。AIOps 平台中额外推送：
- 当前 Stage（解析/调查/诊断/决策/执行）
- 每个动作的 AuditLog 实时事件

#### 5.9.2 CheckPointer

调查流水线挂载 `RedisSaver` 或 `PostgresSaver`：
- 流水线中途失败可从最后 Stage 恢复
- Stage 2 并行调查的部分结果保留，无需重跑已成功的 Agent

#### 5.9.3 AuditLogger

```java
@Component
public class AuditLogger {
    public void logTrigger(ActionRequest req, String triggeredBy) {
        // 写入 action_log 表
    }
    public void logResult(ActionRequest req, ActionResult result) {
        // 更新 action_log 表
    }
}
```

所有 L3 执行动作必须经过 AuditLogger。

---

## 6. 数据存储设计

### 6.1 关系数据库 (PostgreSQL)

**事故主表 `incident`**：
```sql
CREATE TABLE incident (
    id              VARCHAR(36) PRIMARY KEY,
    fingerprint     VARCHAR(64) NOT NULL,
    source          VARCHAR(32) NOT NULL,
    service_name    VARCHAR(64) NOT NULL,
    severity        VARCHAR(8),
    status          VARCHAR(16),
    occurred_at     TIMESTAMP NOT NULL,
    resolved_at     TIMESTAMP,
    summary         TEXT,
    raw_data        JSONB,
    INDEX idx_fingerprint_time (fingerprint, occurred_at),
    INDEX idx_service_status (service_name, status)
);
```

**调查日志 `investigation_log`**：
```sql
CREATE TABLE investigation_log (
    id              BIGSERIAL PRIMARY KEY,
    incident_id     VARCHAR(36) NOT NULL,
    stage           VARCHAR(32),
    agent_name      VARCHAR(64),
    output_text     TEXT,
    evidences       JSONB,
    duration_ms     BIGINT,
    prompt_tokens   INT,
    completion_tokens INT,
    created_at      TIMESTAMP DEFAULT now(),
    INDEX idx_incident (incident_id)
);
```

**动作审计 `action_log`**：
```sql
CREATE TABLE action_log (
    id              BIGSERIAL PRIMARY KEY,
    incident_id     VARCHAR(36) NOT NULL,
    action_type     VARCHAR(32),
    target          VARCHAR(128),
    triggered_by    VARCHAR(64),
    confirmed_by    VARCHAR(64),
    triggered_at    TIMESTAMP NOT NULL,
    executed_at     TIMESTAMP,
    result_status   VARCHAR(16),
    result_message  TEXT,
    INDEX idx_incident (incident_id)
);
```

### 6.2 向量数据库 (Milvus)

见 5.8 节 Collection 设计。

### 6.3 缓存 (Redis)

| Key 模式 | 用途 | TTL |
|---------|------|-----|
| `ops:dedup:{fingerprint}` | 指纹去重计数 | 5min |
| `ops:throttle:notify:{user}` | 通知限流 | 1min |
| `ops:hitl:pending:{incidentId}` | HITL 待确认队列 | 30min |
| `ops:checkpoint:*` | 框架 RedisSaver 检查点 | 24h |

---

## 7. 场景接入示例

### 7.1 邮件告警端到端流程（V1 核心场景）

#### 7.1.1 真实告警邮件示例

```
Subject: [ALERT-PROD] order-service NullPointerException
From: monitor@example.com
Date: 2026-06-03 14:23:50

服务: order-service
环境: prod
主机: prod-order-01.internal
时间: 2026-06-03 14:23:45

异常类: java.lang.NullPointerException
栈顶信息:
  at com.example.order.OrderService.processOrder(OrderService.java:127)
  at com.example.order.OrderController.create(OrderController.java:45)
  at com.example.order.OrderController$$EnhancerBySpringCGLIB$$.create(<generated>)

TraceId: abc123def456
RequestId: req-789xyz
```

#### 7.1.2 端到端处理流程

```
[T+0s]   EmailAlertSource IMAP 拉取
         ↓
[T+0.5s] EmailAlertNormalizer 解析 Subject + Body
         → RawAlert
         ↓
[T+0.5s] IncidentEvent {
           source: "email",
           service: "order-service",
           severity: P1,
           occurredAt: "2026-06-03T14:23:45",
           raw: { subject, body, traceId, host, ... },
           summary: "order-service NPE at OrderService.java:127"
         }
         ↓
[T+0.6s] Deduplicator (Redis) - 5min 内首次出现该指纹 → 放行
         ↓
[T+0.7s] Router → 规则库未命中 → 进入 Agent 流水线
         ↓
[T+1s]   ═══ Stage 1: AlertParserAgent ═══
         输入：邮件原文
         输出 (JSON):
         {
           "service": "order-service",
           "errorClass": "java.lang.NullPointerException",
           "time": "2026-06-03 14:23:45",
           "topFrames": [
             "OrderService.java:127",
             "OrderController.java:45"
           ],
           "host": "prod-order-01.internal",
           "traceId": "abc123def456"
         }
         ↓
[T+2s]   ═══ Stage 2: 并行调查 ═══

         ┌─── LogRetrievalAgent (核心 - 5 策略 SLS 扩展) ───┐
         │ 调用 1: searchLogs(service=order-service,         │
         │                     level=ERROR,                  │
         │                     time=[14:18:45, 14:28:45])    │
         │         → 14 条同时段 ERROR，6 条相同栈            │
         │                                                   │
         │ 调用 2: searchByTraceId(traceId=abc123def456,     │
         │                          time=[14:22:45, 14:24:45])│
         │         → 链路: gateway → order → inventory →     │
         │           payment，payment 返回 null 触发 NPE     │
         │                                                   │
         │ 调用 3: searchLogs(exception=NPE,                 │
         │                     service=order-service,        │
         │                     time=[13:23:45, 14:23:45])    │
         │         → 近 1h 突增 0 → 23 次                    │
         │                                                   │
         │ 调用 4: searchLogs(host=prod-order-01,            │
         │                     time=[14:21:45, 14:25:45])    │
         │         → 仅 1 台 host 报错，非单机问题            │
         │                                                   │
         │ 调用 5: searchLogs(service=order-service,         │
         │                     host=prod-order-01,           │
         │                     time=[14:23:15, 14:23:45])    │
         │         → 错误前 5s 收到 payment 异常响应         │
         └───────────────────────────────────────────────────┘

         (并行) HistoryAgent: Milvus 查 "order NPE payment null"
                → 命中 2 个月前类似故障

         (并行) CodeAnalysisAgent (Phase 2+):
                git log order-service 近 24h
                → 找到 commit a1b2c3 修改了 OrderService:127 附近代码
         ↓
[T+8s]   ═══ Stage 3: RootCauseAgent ═══
         综合 Stage 2 全部输出，产出诊断报告：
         {
           "hypotheses": [
             {
               "confidence": 0.82,
               "description": "payment-service 在 14:23:43 返回 null 响应，order-service 未做 null 检查导致 NPE",
               "evidences": [
                 "trace_link[3]: payment-service 14:23:43.892 response.body=null",
                 "pre_30s[5]: 'received payment response: null' at 14:23:44.011",
                 "history[1]: 2026-04 类似故障，根因为支付侧降级"
               ]
             },
             {
               "confidence": 0.45,
               "description": "可能是 commit a1b2c3 引入的代码缺陷",
               "evidences": [
                 "commit a1b2c3 modified OrderService.java:120-130"
               ]
             }
           ],
           "topConfidence": 0.82,
           "suggestedAction": "联系 payment-service 团队检查 14:23 前后状态；同时建议 order-service 增加 null 检查"
         }
         ↓
[T+8s]   DecisionEngine: confidence 0.82 → HITL_CONFIRM
         ↓
[T+9s]   DingTalkTool 推送诊断卡片（含按钮）
         ↓
         （等待人工 → 选择动作）
         ↓
[T+Xm]   用户确认 → ActionExecutor 执行（或仅记录人工处理）
         ↓
[T+Xm+5s] PostmortemAgent 生成复盘 → Milvus 入库
```

#### 7.1.3 关键设计要点

1. **AlertParserAgent 是无 LLM 的纯解析也可以**：如果邮件格式固定，用正则提取比 LLM 更快更稳。但为保持灵活性，V1 仍用 LLM 处理，后续可优化为"正则优先、LLM 兜底"。

2. **LogRetrievalAgent 必须按 5 策略全调用**：通过系统提示词强制要求，不允许 Agent "偷懒"只调用一两个策略。

3. **每个 SLS 调用要带"必返字段"**：`time`、`host`、`level`、`message`、`trace_id`（如有）。结构化输出便于 RootCauseAgent 引用。

4. **Token 控制**：5 次 SLS 调用每次最多返回 30 条日志，每条 message 截断 300 字符。总输入 Token 预计 < 8000。

### 7.2 Druid 连接池监控

```
DruidProbeSource (@Scheduled 每60s)
  → 检测到 order-svc activeCount=95/maxActive=100
  → IncidentEvent { source: "druid", service: "order-svc", severity: P1 }
  → 流水线运行
  → LogAgent 查最近慢 SQL
  → MetricsAgent 查 DB 负载
  → RootCauseAgent: "慢 SQL X 占用连接 8 分钟未释放"
  → DingTalk 推送
```

### 7.3 服务异常自动扫描 + 代码定位

```
ServiceLogScanSource (@Scheduled 每5min)
  → SLS 查 order-svc 近 5min ERROR 数
  → 当前 50 条 vs 基线 5 条 → 突增
  → IncidentEvent
  → 流水线运行
  → CodeAgent: git log 发现 30min 前发布 commit a1b2c3
  → CodeAgent: git diff a1b2c3 → 修改了 OrderProcessor.java
  → LogAgent: ERROR 都来自 OrderProcessor.calculate()
  → RootCauseAgent: "可能是 commit a1b2c3 引入的 NPE，置信度 85%"
  → HITL 推送，包含 [回滚 a1b2c3] 按钮
```

### 7.4 链路调查（人工触发）

```
POST /api/ops/investigate { traceId: "xyz" }
  → 构造 IncidentEvent { source: "manual" }
  → 跳过 Dedup
  → 走流水线
  → TraceAgent 串联跨服务日志
  → RootCauseAgent: "瓶颈在 payment-svc 第 3 次调用，RT 8s"
  → 返回 SSE 流给用户
```

---

## 8. 包结构规划

```
com.kxh.aiagent.ops/
├── source/                              # 接入层
│   ├── AlertSource.java                 # 统一接口
│   ├── RawAlert.java
│   ├── AlertNormalizer.java
│   ├── EmailAlertSource.java
│   ├── EmailAlertNormalizer.java
│   ├── WebhookAlertSource.java
│   ├── WebhookAlertNormalizer.java
│   ├── DruidProbeSource.java
│   └── ServiceLogScanSource.java
├── model/
│   ├── IncidentEvent.java
│   ├── Severity.java
│   ├── DiagnosisReport.java
│   ├── Hypothesis.java
│   ├── Evidence.java
│   ├── Decision.java
│   ├── DecisionMode.java
│   ├── ActionRequest.java
│   ├── ActionResult.java
│   └── ResolvedIncident.java
├── dedup/
│   ├── IncidentDeduplicator.java
│   └── FingerprintGenerator.java
├── router/
│   ├── IncidentRouter.java
│   └── RuleEngine.java
├── agent/
│   ├── OpsAgentConfig.java              # 流水线编排 (类似 InvestReportAgentConfig)
│   └── prompt/
│       ├── AlertParserPrompts.java
│       ├── LogRetrievalPrompts.java
│       └── ...
├── tool/
│   ├── readonly/                        # L1 只读
│   │   ├── AliyunSlsTool.java
│   │   ├── LocalCodeTool.java           # 主力：filesystem 查找/读取
│   │   ├── GitRepoTool.java             # 按需：仅查历史
│   │   ├── DruidMetricsTool.java
│   │   ├── JvmMetricsTool.java
│   │   └── PrometheusQueryTool.java
│   ├── notify/                          # L2 通知
│   │   ├── DingTalkTool.java
│   │   ├── FeishuTool.java
│   │   └── MailNotifyTool.java
│   └── execute/                         # L3 执行
│       ├── ActionWhitelist.java
│       ├── K8sRestartPodTool.java
│       ├── DeployRollbackTool.java
│       └── ScaleReplicaTool.java
├── decision/
│   ├── DecisionEngine.java
│   ├── ConfidenceEvaluator.java
│   └── HitlConfirmGateway.java          # 钉钉回调
├── execution/
│   ├── ActionExecutor.java              # 接口
│   ├── ActionDispatcher.java
│   └── impls/
│       ├── NotifyActionExecutor.java
│       ├── RestartActionExecutor.java
│       └── RollbackActionExecutor.java
├── knowledge/                           # Milvus 知识库
│   ├── IncidentKnowledgeBase.java       # 接口
│   ├── MilvusIncidentKnowledgeBase.java # 实现
│   └── IncidentEmbeddingService.java
├── audit/
│   └── AuditLogger.java
├── repository/                          # 持久化 (JPA / MyBatis)
│   ├── IncidentRepository.java
│   ├── InvestigationLogRepository.java
│   └── ActionLogRepository.java
└── controller/
    ├── OpsWebhookController.java        # /api/ops/alert/webhook
    ├── OpsInvestigateController.java    # /api/ops/investigate
    └── OpsHitlController.java           # /api/ops/hitl/confirm
```

---

## 9. 部署架构

### 9.1 单体部署（MVP）

所有模块打包为单个 Spring Boot 应用，依赖外部：
- PostgreSQL (复用现有)
- Redis (复用现有)
- Milvus (复用 application-milvus.yml 配置)
- DashScope API (复用现有)
- Aliyun SLS (新增，子账号)

### 9.2 后期可拆分（参考）

- `ops-source-service`：告警接入 + 归一化
- `ops-pipeline-service`：调查流水线（重 LLM 调用，可独立扩缩）
- `ops-action-service`：动作执行（独立审计，独立权限）

通过 Redis Stream / Kafka 解耦。

---

## 10. 分阶段交付计划

| 阶段 | 范围 | 预计周期 | 关键产出 | 上线价值 |
|------|------|---------|---------|---------|
| **Phase 1: V1 核心闭环** | `ops` 包结构 + IncidentEvent + **EmailAlertSource (IMAP)** + **AliyunSlsTool (5 策略上下文扩展)** + AlertParserAgent + LogRetrievalAgent + RootCauseAgent + DingTalkTool | **2 周** | **真实邮件 → SLS 自动检索 → 钉钉诊断推送** | **直接替代人工查日志工作** |
| **Phase 2: 代码分析增强** | **LocalCodeTool（主力）** + GitRepoTool（按需） + 代码定时同步 + CodeAnalysisAgent + TraceAgent | 1 周 | 增加代码层定位能力 | 诊断假设更准确 |
| **Phase 3: 主动巡检扩展** | WebhookAlertSource + DruidProbeSource + ServiceLogScanSource | 1 周 | 从被动接告警 → 主动发现 | 覆盖更多场景 |
| **Phase 4: Milvus 知识库** | MilvusIncidentKnowledgeBase + HistoryAgent + 复盘自动入库 | 1 周 | 越用越聪明 | 体现 AI 价值 |
| **Phase 5: HITL + 受控动作** | DecisionEngine + HitlConfirmGateway + K8sRestart + Rollback + AuditLog | 2 周 | 真正闭环 | 完整 AIOps |
| **Phase 6: 生产化** | PostgresSaver CheckPointer + 多租户(服务负责人映射) + 监控大盘 + RAM 权限收敛 | 1 周 | 生产就绪 | 可上线 |

**总周期约 8 周（2 个月）**

**Phase 1 即可独立上线** —— 它就是 V1 核心交付目标，覆盖了当前最高频的人工工作（收邮件 → 查 SLS → 出诊断）。Phase 2-6 都是在 V1 基础上的渐进增强。

---

## 10.5 成本控制策略

### 10.5.1 七层成本防御

| 层 | 机制 | 拦截率 | 实施位置 |
|----|------|--------|---------|
| L1 | 指纹去重 (Redis 5min) | 60-80% | Deduplicator |
| L2 | Burst Suppression (1min 同 service ≥N 条进聚合) | 10-20% | Router |
| L3 | 全局 QPS 限流 (max 10 incidents/min) | 兜底 | Router |
| L4 | 规则库快速通道 (已知模式跳过 LLM) | 30-50% | RuleEngine |
| L5 | 工具结果裁剪 (SLS ≤30 条/300 字符，Code ≤200 行) | 控量 | Tool 层 |
| L6 | Agent 调用上限 (ModelCallLimit, ToolCallLimit) | 防死循环 | AgentHook |
| L7 | Incident 总预算硬上限 (≤25K tokens) | 兜底 | ProgressHook |

实际成本 = 裸跑成本 × **1-3%**

### 10.5.2 Storm 模式（告警风暴专项）

**触发条件**（任一成立）：
- 5min 内同一 service 收到 ≥ 50 条告警
- 5min 内同一 fingerprint 出现 ≥ 20 次
- 全平台 1min 内 ≥ 100 条 raw alerts

**Storm 行为**：
1. 暂停逐条处理，进入 60s 聚合窗口
2. 生成 1 个聚合 IncidentEvent（包含统计信息和典型 fingerprints）
3. 流水线只跑一次，时间窗扩大到 30min
4. 钉钉推送加红色高优标记

**节省效果**：300 条告警单根因从 6M tokens → 30K tokens（99.5% 节省）

### 10.5.3 三层预算系统

```yaml
ops:
  budget:
    incident:
      max-tokens: 25000           # 单次 incident 上限
      timeout: 60s
    service-daily:
      max-tokens: 200000          # 单 service 每日上限
      action-on-exceed: degrade   # 超出降级
    global-daily:
      max-tokens: 5000000         # 全平台日上限
      action-on-exceed: pause
      alert-at: 0.8               # 80% 告警
```

埋点：`ProgressHook.afterAgent` 累加 tokens 到 Redis 滑动窗口；超限抛 `BudgetExceededException` 中断流水线转 HITL。

### 10.5.4 降级链 (Degradation Cascade)

```
正常模式 (全 Agent + qwen-plus)
    ↓ service-daily 80%
轻量模式 (仅 LogRetrieval + RootCause)
    ↓ service-daily 100%
规则模式 (无 LLM，仅规则库)
    ↓ global 100%
暂停模式 (告警入队，明日恢复)
```

### 10.5.5 模型分层

| Agent | 模型 | 理由 |
|-------|------|------|
| AlertParserAgent | qwen-turbo | 简单结构化提取 |
| LogRetrievalAgent | qwen-plus | 需要策略推理 |
| CodeAnalysisAgent | qwen-plus | 一般代码理解 |
| HistoryAgent | qwen-turbo | 主要 RAG，LLM 只总结 |
| RootCauseAgent | **qwen-max** | 关键诊断，质量优先 |

相比统一 qwen-max 可节省 **40-60%** 成本。

### 10.5.6 LLM 响应缓存

同 fingerprint 1h 内已诊断 → 直接返回缓存（标注 `[CACHED]`）。HITL 模式下用户可选择重新诊断。

### 10.5.7 实际成本估算 (qwen-plus 基线)

| 场景 | 日告警量 | 去重后 | 日成本 | 月成本 |
|------|---------|-------|--------|--------|
| 平稳期 | 200 | 30 | ¥1.3 | ¥40 |
| 高峰期 | 1000 | 80 | ¥3.5 | ¥105 |
| 告警风暴日 | 10000 | Storm 100 | ¥10 | / |

预算硬上限设为 **¥150/月**（≈ 5M tokens/天），实际用量应在 50% 以内。

### 10.5.8 Cost Dashboard

```sql
CREATE TABLE cost_metrics (
    date              DATE,
    service_name      VARCHAR(64),
    agent_name        VARCHAR(64),
    incident_count    INT,
    total_tokens      BIGINT,
    total_cost_cny    DECIMAL(10,4),
    INDEX (date, service_name)
);
```

ProgressHook 在每次流水线结束时写一条；前端展示：服务对比 / 时间趋势 / Top10 贵 incident 排行。

---

## 11. 风险与缓解措施

| 风险类型 | 具体风险 | 缓解措施 |
|---------|---------|---------|
| **安全** | AK/SK 泄露 | RAM 子账号 + 环境变量 + 不入 git + 定期轮换 |
| **安全** | Agent 错误执行高危动作 | 三级工具分级 + 白名单 + HITL 双确认 |
| **安全** | 命令注入 / SQL 注入 | InputValidator + 参数化查询 + 只查白名单库 |
| **稳定性** | 告警风暴打爆 LLM 配额 | 指纹去重 + 5min 聚合 + 优先级丢弃 + 限流 |
| **稳定性** | 单次调用 Token 爆炸 | Tool 结果裁剪 + 单条日志 ≤500 字符 + 最多 50 条 |
| **稳定性** | 流水线中途失败 | CheckPointer 断点续跑 + 幂等设计 |
| **正确性** | LLM 幻觉编造日志/提交 | 强制 Evidence 引用 + JSON Schema 校验 |
| **正确性** | RAG 召回老旧方案误导 | 时间衰减权重 + 相似度阈值 0.75 |
| **正确性** | 路由规则误命中 | 规则可禁用 + 命中后仍允许 fallback 到 Agent |
| **观测性** | 平台自身故障无人知 | 不能 dogfood — 平台告警走独立钉钉机器人 |
| **成本** | LLM 调用成本失控 | 分层策略（规则优先）+ 实时成本追踪 + 月度预算告警 |

---

## 12. 与现有项目的复用

| 现有能力 | 在 AIOps 平台中的复用 |
|---------|--------------------|
| SequentialAgent + ParallelAgent | 调查流水线编排 |
| ReactAgent | 各调查 Agent 实现 |
| AgentHook 体系 | ProgressHook 全链路监控 |
| ProgressEventBus + StreamProgressEmitter | SSE 实时推送调查进度 |
| AgentProgressEvent（含 duration/tokens） | 成本与耗时追踪 |
| CheckPointer (MemorySaver/RedisSaver) | 流水线断点续跑 |
| Milvus 向量库（application-milvus.yml） | 历史故障知识库 |
| DashScope EmbeddingModel | 故障文本向量化 |
| ToolCallback 体系 | L1/L2/L3 工具统一注册 |
| InputValidator | 工具参数白名单校验 |
| Spring Mail（如已配） | EmailAlertSource 基础设施 |

**复用率预估 ≥ 80%**。本质上是给现有 Agent 框架插上 AIOps 场景，而非另起炉灶。

---

## 13. 附录

### 13.1 配置示例 (application-ops.yml)

```yaml
ops:
  source:
    email:
      enabled: false
      host: imap.example.com
      port: 993
      username: ${OPS_EMAIL_USER:}
      password: ${OPS_EMAIL_PASS:}
      folder: INBOX
      poll-interval: 30s
    webhook:
      enabled: true
      path: /api/ops/alert/webhook
    druid:
      enabled: false
      probe-interval: 60s
      services: []
    log-scan:
      enabled: false
      scan-interval: 5m
      services: []
  
  sls:
    endpoint: cn-hangzhou.log.aliyuncs.com
    project: log-service-1679531816490379-cn-hangzhou
    access-key-id: ${ALIYUN_SLS_AK:}
    access-key-secret: ${ALIYUN_SLS_SK:}
    logstore-whitelist:
      - java-service-delivery
      - access-log
  
  git:
    repo-whitelist:
      - name: order-service
        path: /opt/repos/order-service
      - name: payment-service
        path: /opt/repos/payment-service
  
  knowledge:
    backend: milvus
    collection: incident_knowledge
    embedding-model: text-embedding-v2
    embedding-dim: 1536
    top-k: 5
    similarity-threshold: 0.75
    time-decay-days: 180
  
  decision:
    confidence-auto-threshold: 0.9
    confidence-hitl-threshold: 0.5
  
  action:
    whitelist:
      auto:
        - { type: "notify", targets: ["*"] }
      hitl:
        - { type: "restart_pod", targets: ["*/*"] }
        - { type: "rollback", targets: ["*/*"] }
      forbidden:
        - { type: "delete_*", targets: ["*"] }
  
  notify:
    dingtalk:
      webhook: ${OPS_DINGTALK_WEBHOOK:}
      secret: ${OPS_DINGTALK_SECRET:}
    feishu:
      webhook: ${OPS_FEISHU_WEBHOOK:}
```

### 13.2 SLS 查询语法速查

```
# 关键词
level:ERROR
"Connection refused"
service:order-svc AND level:ERROR

# 时间范围（API 参数，非语法）
__time__ >= 1700000000 AND __time__ < 1700003600

# 链路
trace_id:abc123

# SQL 聚合
* | SELECT host, count(*) AS cnt GROUP BY host ORDER BY cnt DESC LIMIT 10
* | SELECT level, count(*) GROUP BY level
```

### 13.3 Milvus Java SDK 关键依赖

```xml
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>2.4.x</version>
</dependency>
```

注：项目可能已通过 `spring-ai-milvus-store-spring-boot-starter` 间接引入，需确认。

### 13.4 钉钉交互式卡片消息示例

```json
{
  "msgtype": "actionCard",
  "actionCard": {
    "title": "[P1] order-service 异常告警",
    "text": "## 诊断报告\n**置信度**: 78%\n**根因假设**: commit a1b2c3 引入 NPE\n**证据**: order.log:1428",
    "btnOrientation": "1",
    "btns": [
      { "title": "确认回滚", "actionURL": "https://.../api/ops/hitl/confirm?id=xxx&action=rollback" },
      { "title": "转人工", "actionURL": "https://.../api/ops/hitl/confirm?id=xxx&action=escalate" }
    ]
  }
}
```

---

**文档结束**
