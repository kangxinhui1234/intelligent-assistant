package com.kxh.aiagent.ops.tool;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogContent;
import com.aliyun.openservices.log.common.QueriedLog;
import com.aliyun.openservices.log.request.GetLogsRequest;
import com.aliyun.openservices.log.response.GetLogsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AliyunSlsTool {

    private static final Logger log = LoggerFactory.getLogger(AliyunSlsTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ops.sls.endpoint:}")
    private String endpoint;
    @Value("${ops.sls.project:}")
    private String project;
    @Value("${ops.sls.access-key-id:}")
    private String accessKeyId;
    @Value("${ops.sls.access-key-secret:}")
    private String accessKeySecret;
    @Value("${ops.sls.default-logstore:java-service-delivery}")
    private String defaultLogstore;
    @Value("${ops.sls.max-results-per-query:30}")
    private int maxResults;
    @Value("${ops.sls.message-truncate-chars:300}")
    private int truncateChars;

    private Client client;
    private boolean mockMode = false;

    @PostConstruct
    public void init() {
        if (accessKeyId == null || accessKeyId.isBlank() ||
            accessKeySecret == null || accessKeySecret.isBlank() ||
            endpoint == null || endpoint.isBlank()) {
            mockMode = true;
            log.warn("SLS AK/SK 未配置，AliyunSlsTool 进入 Mock 模式（返回示例数据）");
        } else {
            client = new Client(endpoint, accessKeyId, accessKeySecret);
            log.info("AliyunSlsTool 已就绪 endpoint={} project={}", endpoint, project);
        }
    }

    @Tool(description = """
            策略1-时间窗扩展：检索指定服务在 [time-5min, time+5min] 范围内的 ERROR 日志。
            用于判断是否多实例同时报错。""")
    public String searchByTimeWindow(
            @ToolParam(description = "服务名") String service,
            @ToolParam(description = "异常发生时间戳(秒)") long timeSec) {
        long from = timeSec - 300;
        long to = timeSec + 300;
        String query = "service:" + safe(service) + " AND level:ERROR";
        return executeQuery("time_window", defaultLogstore, query, from, to);
    }

    @Tool(description = """
            策略2-链路扩展：按 TraceId 检索全链路日志，时间窗 [time-1min, time+1min]。
            用于看上下游服务调用链路。""")
    public String searchByTraceId(
            @ToolParam(description = "TraceId") String traceId,
            @ToolParam(description = "异常发生时间戳(秒)") long timeSec) {
        long from = timeSec - 60;
        long to = timeSec + 60;
        String query = "trace_id:" + safe(traceId);
        return executeQuery("trace_link", defaultLogstore, query, from, to);
    }

    @Tool(description = """
            策略3-异常类扩展：检索同服务该异常类近 1h 出现情况，时间窗 [time-1h, time]。
            用于判断异常是否突增。""")
    public String searchByErrorClass(
            @ToolParam(description = "服务名") String service,
            @ToolParam(description = "异常类完整名,如java.lang.NullPointerException") String errorClass,
            @ToolParam(description = "异常发生时间戳(秒)") long timeSec) {
        long from = timeSec - 3600;
        long to = timeSec;
        String query = "service:" + safe(service) + " AND \"" + safe(errorClass) + "\"";
        return executeQuery("error_class_trend", defaultLogstore, query, from, to);
    }

    @Tool(description = """
            策略4-主机扩展：检索指定主机近 [time-2min, time+2min] 的所有 WARN/ERROR。
            用于判断是否单机问题。""")
    public String searchByHost(
            @ToolParam(description = "主机名") String host,
            @ToolParam(description = "异常发生时间戳(秒)") long timeSec) {
        long from = timeSec - 120;
        long to = timeSec + 120;
        String query = "host:" + safe(host) + " AND (level:ERROR OR level:WARN)";
        return executeQuery("host_scope", defaultLogstore, query, from, to);
    }

    @Tool(description = """
            策略5-上下文扩展：检索异常前 30 秒该服务该主机的所有日志（含 INFO）。
            用于看错误发生前的操作上下文。""")
    public String searchPre30sContext(
            @ToolParam(description = "服务名") String service,
            @ToolParam(description = "主机名") String host,
            @ToolParam(description = "异常发生时间戳(秒)") long timeSec) {
        long from = timeSec - 30;
        long to = timeSec;
        String query = "service:" + safe(service) + " AND host:" + safe(host);
        return executeQuery("pre_30s_context", defaultLogstore, query, from, to);
    }

    private String executeQuery(String strategy, String logstore, String query, long fromSec, long toSec) {
        if (mockMode) {
            return mockResponse(strategy, query, fromSec, toSec);
        }
        try {
            GetLogsRequest req = new GetLogsRequest(project, logstore, (int) fromSec, (int) toSec, "", query);
            req.SetLine(maxResults);
            GetLogsResponse resp = client.GetLogs(req);
            return formatResponse(strategy, resp.getLogs());
        } catch (Exception e) {
            log.error("SLS 查询失败 strategy={} query={} err={}", strategy, query, e.getMessage());
            return "{\"strategy\":\"" + strategy + "\",\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String formatResponse(String strategy, List<QueriedLog> logs) {
        ObjectNode root = mapper.createObjectNode();
        root.put("strategy", strategy);
        root.put("count", logs == null ? 0 : logs.size());
        ArrayNode arr = root.putArray("logs");
        if (logs != null) {
            for (QueriedLog l : logs) {
                ObjectNode item = arr.addObject();
                List<LogContent> contents = l.GetLogItem().GetLogContents();
                if (contents != null) {
                    for (LogContent c : contents) {
                        String key = c.GetKey();
                        String value = c.GetValue() == null ? "" : c.GetValue();
                        if (value.length() > truncateChars) value = value.substring(0, truncateChars) + "...";
                        item.put(key, value);
                    }
                }
            }
        }
        return root.toString();
    }

    private String mockResponse(String strategy, String query, long fromSec, long toSec) {
        ObjectNode root = mapper.createObjectNode();
        root.put("strategy", strategy);
        root.put("mode", "MOCK");
        root.put("query", query);
        root.put("time_range", fromSec + "~" + toSec);
        ArrayNode arr = root.putArray("logs");

        ObjectNode l1 = arr.addObject();
        l1.put("time", String.valueOf(toSec));
        l1.put("level", "ERROR");
        l1.put("service", "order-service");
        l1.put("host", "prod-order-01");
        l1.put("trace_id", "abc123def456");
        l1.put("message", "[MOCK] NullPointerException at OrderService.processOrder(OrderService.java:127), caused by payment response null");

        ObjectNode l2 = arr.addObject();
        l2.put("time", String.valueOf(toSec - 5));
        l2.put("level", "WARN");
        l2.put("service", "payment-service");
        l2.put("host", "prod-payment-02");
        l2.put("trace_id", "abc123def456");
        l2.put("message", "[MOCK] payment timeout, returning null response");

        return root.toString();
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[\"\\\\]", "");
    }
}
