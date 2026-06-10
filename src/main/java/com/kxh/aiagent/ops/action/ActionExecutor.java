package com.kxh.aiagent.ops.action;

import com.kxh.aiagent.ops.entity.OpsActionLog;

/**
 * HITL 动作执行器 — 审批通过后由 OpsActionController 异步调用。
 * <p>
 * 实现类必须:
 *  - 声明自己负责的 {@link ActionType}
 *  - execute 内部不要抛 checked exception,失败统一返回 ExecutionResult.failed(msg)
 */
public interface ActionExecutor {

    ActionType type();

    ExecutionResult execute(OpsActionLog log);

    record ExecutionResult(String status, String message) {
        public static ExecutionResult ok(String message)     { return new ExecutionResult("EXECUTED", message); }
        public static ExecutionResult failed(String message) { return new ExecutionResult("FAILED",   message); }
    }
}
