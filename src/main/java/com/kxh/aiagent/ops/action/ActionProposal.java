package com.kxh.aiagent.ops.action;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Agent 经过分析后产出的"建议动作"提议。
 * 由 ProposeActionTool 序列化后落 ops_action_log,等待人工审批。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionProposal(
        ActionType actionType,
        String target,
        Map<String, Object> params,
        String rationale
) {
}
