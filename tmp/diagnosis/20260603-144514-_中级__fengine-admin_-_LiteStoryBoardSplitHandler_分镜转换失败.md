# [中级] fengine-admin - LiteStoryBoardSplitHandler 分镜转换失败

_生成时间: 2026-06-03T14:45:14.010806900_

---

## 根因假设
### 1. 轻创作分镜提取服务在流式响应完成时发生 BizException，核心逻辑 `LiteStoryBoardSplitHandler.onStreamComplete` 在第232行抛出“轻创作提取分镜列表转换失败”
置信度: 95%
- 证据: 2026-06-03 10:14:18.141 | 1e1f6cf743fee0b5-6534ffff5dd80-39088a2 | ERROR ... LiteStoryBoardSplitHandler.onStreamComplete(LiteStoryBoardSplitHandler.java:232) : Silent task subscription error com.yiittou.framework.common.core.exception.BizException: 轻创作提取分镜列表转换失败

### 2. AI Chat 流式任务订阅静默失败，表明 streamChat-8 线程池中某 worker 处理异常后未正确传播或重试
置信度: 70%
- 证据: 2026-06-03 10:14:18.141 | 1e1f6cf743fee0b5-6534ffff5dd80-39088a2 | ERROR ... /fengine/admin/project/retrySplitStoryboard --- [streamChat-8] c.y.f.b.admin.service.chat.AiChatService : Silent task subscription error

### 3. 分镜拆分依赖的上游服务（如轻创作模型/存储）在该时刻不可用或返回非法结构，导致反序列化或业务校验失败
置信度: 60%
- 证据: 同一 traceId `4lQXvBQfEETvLuict9Zz4E-QFAcNq8Nbg` 下无其他上下文日志（SLS 查询为空），暗示异常发生在 handler 内部无下游调用，或下游调用未打点

## 建议动作
1. 立即检查 `LiteStoryBoardSplitHandler.java` 第232行附近代码，确认分镜列表转换逻辑（如 JSON 解析、DTO 映射、空值校验）是否存在 NPE 或格式兼容性缺陷
2. 检查 `AiChatService` 中 `streamChat-8` 线程池配置（core/max/queue）及近期拒绝/超时指标，排除资源耗尽导致静默失败
3. 补充 `LiteStoryBoardSplitHandler.onStreamComplete` 入参与关键中间变量的 TRACE 日志（尤其 `response`, `splitResult`, `storyboardList`），并确保所有 BizException 均携带完整上下文与 traceId 打点