package com.kxh.aiagent.ops.service;

import com.kxh.aiagent.ops.model.IncidentEvent;

import java.util.Map;

/** 把 IncidentEvent 转为 Agent 流水线可读的纯文本格式 */
public final class PipelineInputBuilder {

    private PipelineInputBuilder() {}

    public static String build(IncidentEvent incident) {
        Map<String, Object> raw = incident.raw();
        StringBuilder sb = new StringBuilder();
        sb.append("incidentId: ").append(incident.incidentId()).append('\n');
        sb.append("--- 告警邮件标题 ---\n");
        sb.append(String.valueOf(raw.get("subject"))).append('\n');
        sb.append("--- 告警邮件正文 ---\n");
        sb.append(String.valueOf(raw.get("body"))).append('\n');
        // 转义残留 { } 避免 Spring AI StringTemplate 误解析
        return sb.toString().replace("{", "\\{").replace("}", "\\}");
    }
}
