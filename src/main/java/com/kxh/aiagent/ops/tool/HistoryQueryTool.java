package com.kxh.aiagent.ops.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kxh.aiagent.ops.history.OpsHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 历史故障 RAG 工具 — 混合检索 (BM25 + dense) 返回 Top3 相似历史事故。
 */
@Component
public class HistoryQueryTool {

    private static final Logger log = LoggerFactory.getLogger(HistoryQueryTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private OpsHistoryService historyService;

    @Tool(description = """
            查询历史相似故障 — 使用 Milvus BM25 + dense vector 混合检索。
            必须提供查询文本(异常描述/根因关键词)。
            可选: service 用于精确过滤同服务历史; errorClass 暂未启用过滤但记录在内。
            返回 Top3 历史事故,含 score / service / error_class / summary / resolution。
            若历史库未启用或无相似结果,返回 not_available 或空数组。""")
    public String queryHistory(
            @ToolParam(description = "查询文本,建议包含 service+异常类+核心描述") String queryText,
            @ToolParam(description = "服务名,用于过滤同服务历史(可空)") String service,
            @ToolParam(description = "异常类名(可空)") String errorClass) {
        if (historyService == null) {
            return "{\"status\":\"not_available\",\"reason\":\"ops.history.enabled=false\"}";
        }
        if (!historyService.isReady()) {
            return "{\"status\":\"not_ready\",\"reason\":\"Milvus collection 未加载或 embedding model 未配置\"}";
        }
        try {
            List<Map<String, Object>> hits = historyService.hybridSearch(queryText, service, errorClass);
            ObjectNode root = mapper.createObjectNode();
            root.put("status", "ok");
            root.put("count", hits.size());
            root.put("query", queryText);
            ArrayNode arr = root.putArray("results");
            for (Map<String, Object> h : hits) {
                ObjectNode item = arr.addObject();
                item.put("score", String.valueOf(h.get("score")));
                Object entity = h.get("entity");
                if (entity instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> item.put(String.valueOf(k), String.valueOf(v)));
                }
            }
            return root.toString();
        } catch (Exception e) {
            log.error("queryHistory 失败: {}", e.getMessage(), e);
            return "{\"status\":\"error\",\"reason\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
