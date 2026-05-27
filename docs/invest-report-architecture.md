# 智能研报自动生成系统 — 架构文档

## 一、系统概览

基于 Spring AI Alibaba Agent Framework 的多 Agent 协作系统，输入股票代码，自动生成专业投资分析研报。核心特点：

- **数据准确**——通过 AKShare 微服务获取 A 股官方披露数据，不依赖网络搜索
- **分析全面**——10 个专用 Agent 覆盖卖方研报全部分析维度
- **实时可见**——SSE 流式推送，前端可看到每个 Agent 的分析进度
- **并行加速**——8 个分析 Agent 同时运行，总耗时缩短 60%+

## 二、系统架构图

```
┌──────────────────────────────────────────────────┐
│                   前端 UI                         │
│   输入: 股票代码   输出: 实时进度 + Markdown/PDF   │
└──────────────┬──────────────────┬────────────────┘
               │ SSE (实时推送)    │ HTTP (文件下载)
               ▼                   ▼
┌──────────────────────────┐  ┌──────────────┐
│  ReactAgentController    │  │ 静态文件服务   │
│  /v2/agent/invest/       │  │ /api/files/  │
│  analysis                │  │              │
└──────────┬───────────────┘  └──────────────┘
           │
           ▼
┌──────────────────────────────────────────────────┐
│          SequentialAgent (顶层编排)               │
│                                                   │
│  Step 1 → Step 2 → Step 3 → Step 4               │
│  ────────┼────────┼────────┼───────               │
│          │        │        │                      │
│  ┌───────┴──┐ ┌───┴────┐ ┌┴──────────┐ ┌───────┐ │
│  │数据采集  │ │并行分析│ │投资建议   │ │研报   │ │
│  │Agent     │ │集群    │ │Agent      │ │生成   │ │
│  │          │ │(8Agent)│ │           │ │Agent  │ │
│  └───────┬──┘ └───┬────┘ └─────┬─────┘ └───┬───┘ │
│          │        │            │            │     │
└──────────┼────────┼────────────┼────────────┼─────┘
           │        │            │            │
           ▼        ▼            ▼            ▼
┌──────────┴────────┴────────────┴────────────┴─────┐
│                   工具层                            │
│  FinanceReader | ValuationReader | IndustryReader  │
│  MarkdownWriter | PDFGenerator                    │
└─────────────────────┬─────────────────────────────┘
                      │ HTTP (localhost:8899)
                      ▼
┌──────────────────────────────────────────────────┐
│          AKShare 数据微服务 (Python/FastAPI)       │
│  /finance | /valuation | /stock_info | /industry │
│  底层: akshare → 东方财富/雪球 A 股官方数据        │
└──────────────────────────────────────────────────┘
```

## 三、Agent 清单（10 Agent）

| # | Agent | 类型 | 工具 | 阶段 |
|---|-------|------|------|------|
| 1 | 数据采集Agent | ReactAgent | FinanceReader, ValuationReader, IndustryReader (stock_info + peers) | 串行① |
| 2 | 商业模式Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 3 | 行业分析Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 4 | 杜邦分析Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 5 | 盈利能力Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 6 | 成长性分析Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 7 | 现金流分析Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 8 | 估值分析Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 9 | 风险识别Agent | ReactAgent | 无(纯LLM推理) | 并行② |
| 10 | 投资建议Agent | ReactAgent | 无(纯LLM推理) | 串行③ |
| 11 | 研报生成Agent | ReactAgent | MarkdownWriter, PDFGenerator | 串行④ |

### 各 Agent 职责

#### Step 1: 数据采集Agent
- **输入**：股票代码（如 `600519`）
- **工具**：
  - `readFinanceData(code, years)` — 利润表/资产负债表/现金流量表 + ROE/ROA/毛利率等核心指标
  - `readValuationData(code, years)` — PE-TTM/PB/PS/PEG/市值
  - `readStockInfo(code)` — 公司基本信息（主营业务/行业/上市日期等）
  - `readIndustryPeers(code)` — 同行业可比公司列表
- **输出**：结构化 JSON 数据集
- **数据来源**：AKShare → 东方财富/雪球 A 股官方披露

#### Step 2: 并行分析集群 (8 Agent)

| Agent | 分析维度 | 输出示例 |
|-------|---------|---------|
| 商业模式 | 主营业务、护城河、竞争壁垒、收入结构 | "白酒龙头，品牌护城河极深，高端产品占比85%" |
| 行业分析 | 行业景气度、竞争格局、政策环境 | "白酒行业CR5提升至60%，高端化趋势明显" |
| 杜邦分析 | ROE = 净利率 × 周转率 × 权益乘数拆解 | "ROE 30% = 净利率50% × 周转0.6 × 杠杆1.0" |
| 盈利能力 | 毛利率/净利率趋势、费用率 | "毛利率92%、连续5年稳定，三费占比低于行业均值" |
| 成长性分析 | 营收/利润 CAGR、增长驱动力 | "5年营收CAGR 15%，利润CAGR 18%，量价齐升" |
| 现金流质量 | 经营现金流/净利润比、自由现金流 | "经营现金流/净利润=1.1，自由现金流充裕" |
| 估值分析 | PE/PB 历史分位、行业对比、PEG | "PE 25x处于历史20%分位，PEG 0.8偏低" |
| 风险识别 | 财务+经营+行业+宏观四维风险 | "负债率30%安全，核心风险：政策收紧、消费降级" |

#### Step 3: 投资建议Agent
- **输入**：8 份分析结论 + 原始数据
- **输出**：评级（买入/增持/持有/减持/卖出）+ 目标价区间 + 核心逻辑

#### Step 4: 研报生成Agent
- **输入**：全部分析结论 + 投资建议
- **工具**：`generateMarkdown(fileName, markdownContent)` → HTTP 下载地址
- **输出**：完整 Markdown 研报 + PDF

## 四、研报输出结构

```markdown
# 【股票名称】(股票代码) 投资分析报告

## 一、公司概况
（商业模式 + 行业地位）

## 二、财务分析
### 2.1 盈利能力（含杜邦分析）
### 2.2 成长性分析
### 2.3 现金流质量

## 三、估值分析
（历史分位 + 行业对比 + 合理区间判断）

## 四、风险提示
（财务/经营/行业/宏观风险分级）

## 五、投资建议与评级
（评级 + 目标价 + 核心逻辑）
```

## 五、数据层

### AKShare 微服务

| 端点 | 说明 | 数据来源 |
|------|------|---------|
| `/api/v1/finance/{code}?years=5` | 财务三表(仅年报) + 核心指标 | 东方财富 |
| `/api/v1/valuation/{code}?years=5` | PE/PB/PS/PEG/市值 | 东方财富 |
| `/api/v1/stock_info/{code}` | 公司基本信息(40+字段) | 雪球 |
| `/api/v1/industry/peers/{code}` | 同行业可比公司 | 东方财富 |

### 数据处理策略
- 仅返回**年报**数据（过滤一季报/中报/三季报）
- 时间戳自动转换为 `YYYY-MM-DD` 格式
- 列精简：利润表 15 列 / 资产负债表 15 列 / 现金流量表 12 列（完整版 200+ 列）
- 数据量：~9KB / 次请求（精简前 ~52KB）

## 六、SSE 事件协议

每步 Agent 产出即时推送：

```
event: message
data: 数据采集Agent → 已获取2021-2025年财务数据...

event: message
data: 杜邦分析Agent → ROE从9.53%恢复至13.47%，主要驱动力为净利率回升...

event: message
data: 估值分析Agent → 当前PE-TTM 8.5x处于历史15%分位，显著低于行业均值...

event: message
data: 研报生成Agent → 报告已生成: http://localhost:8092/api/files/中国平安投资分析报告.md

event: done
data: [DONE]
```

## 七、技术栈

| 层 | 技术 |
|----|------|
| Agent 框架 | Spring AI Alibaba Agent Framework 1.1.2.1 |
| 编排 | SequentialAgent + ParallelAgent 嵌套 |
| LLM | DashScope (通义千问) |
| 数据源 | AKShare → 东方财富/雪球 A 股数据 |
| 数据微服务 | Python FastAPI + uvicorn (端口 8899) |
| 后端框架 | Spring Boot 3.x + Java 21 (GraalVM) |
| SSE 流式 | Flux<Message> → SseEmitter |
| 前端 | Vue 3 + SSE EventSource |

## 八、文件清单

```
项目根目录/
├── akshare-service/              # Python 数据微服务
│   ├── main.py                   # FastAPI 入口
│   ├── requirements.txt
│   ├── start.bat
│   └── router/
│       ├── __init__.py           # 工具函数 + 列过滤 + 年报筛选
│       ├── finance.py            # 财务三表 + 核心指标
│       ├── valuation.py          # 估值指标
│       └── industry.py           # 公司信息 + 行业对比
│
├── src/main/java/com/kxh/aiagent/
│   ├── agent/config/
│   │   ├── ReactAgentConfig.java        # 通用Agent + 单Agent投资
│   │   └── InvestReportAgentConfig.java # ★ 10 Agent 研报流水线
│   ├── controller/
│   │   └── ReactAgentController.java    # /v2/agent/chat, invest, invest/analysis
│   └── tools/finance/
│       ├── FinanceApiClient.java        # HTTP 客户端 (120s超时)
│       ├── RemoteFinanceDataReader.java  # @Tool: 财务数据
│       ├── RemoteValuationReader.java    # @Tool: 估值数据
│       └── RemoteIndustryReader.java     # @Tool: 公司信息 + 行业
│
└── docs/
    └── invest-report-architecture.md    # ★ 本文档
```

## 九、启动步骤

1. **启动数据微服务**：
   ```bash
   cd akshare-service
   python -m uvicorn main:app --host 0.0.0.0 --port 8899
   ```

2. **启动 Spring Boot**：
   ```bash
   JAVA_HOME="/c/Users/a/.jdks/graalvm-jdk-21.0.7" mvn spring-boot:run
   ```

3. **测试接口**：
   ```
   GET /v2/agent/invest/analysis?message=分析600519贵州茅台
   ```
