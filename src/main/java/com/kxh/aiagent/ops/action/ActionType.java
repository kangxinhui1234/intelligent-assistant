package com.kxh.aiagent.ops.action;

/**
 * HITL 动作类型 — 第一批只接 3 个最安全的:
 * <ul>
 *   <li>{@link #SUPPRESS_FINGERPRINT} — 屏蔽同 fingerprint 告警 N 小时(写 Redis)</li>
 *   <li>{@link #NOTIFY_OWNER}          — 把完整诊断报告邮件发给指定负责人</li>
 *   <li>{@link #MARK_AS_KNOWN}         — 登记为已知问题,写入 Milvus 历史 RAG</li>
 * </ul>
 * 后续新增动作只需:
 * 1) 在此枚举追加;
 * 2) 实现 {@link com.kxh.aiagent.ops.action.ActionExecutor};
 * 3) 在 ProposeActionTool 的 schema 描述里补充。
 */
public enum ActionType {
    SUPPRESS_FINGERPRINT,
    NOTIFY_OWNER,
    MARK_AS_KNOWN;

    public static ActionType fromString(String s) {
        if (s == null) return null;
        try { return ActionType.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
