package com.kxh.aiagent.ops.history;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 历史故障 RAG 服务 — 混合检索 (BM25 + dense vector + RRF rerank)。
 */
@Service
@ConditionalOnProperty(name = "ops.history.enabled", havingValue = "true", matchIfMissing = false)
public class OpsHistoryService {

    private static final Logger log = LoggerFactory.getLogger(OpsHistoryService.class);

    @Value("${ops.history.milvus.database:default}") private String database;
    @Value("${ops.history.milvus.collection:ops_incident_kb}") private String collection;
    @Value("${ops.history.top-k:3}") private int topK;

    @Autowired private MilvusClientV2 client;
    @Autowired(required = false)
    @Qualifier("dashscopeEmbeddingModel")
    private EmbeddingModel embeddingModel;

    private boolean collectionLoaded = false;

    @PostConstruct
    public void warmup() {
        tryLoad();
    }

    private synchronized void tryLoad() {
        if (collectionLoaded) return;
        try {
            client.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collection).build());
            collectionLoaded = true;
            log.info("[Milvus] Collection {} 已加载到内存", collection);
        } catch (Exception e) {
            log.warn("[Milvus] Collection 加载推迟,首次操作时重试: {}", e.getMessage());
        }
    }

    public boolean isReady() {
        if (embeddingModel == null) return false;
        if (!collectionLoaded) tryLoad();
        return collectionLoaded;
    }

    /** 把一条诊断结果写入(或更新) Milvus */
    public void upsert(OpsHistoryRecord record) {
        try {
            float[] dense = embed(record.fullText());
            JsonObject row = new JsonObject();
            row.addProperty("id", record.id());
            row.addProperty("service_name", safe(record.serviceName(), 128));
            row.addProperty("error_class", safe(record.errorClass(), 256));
            row.addProperty("severity", safe(record.severity(), 16));
            row.addProperty("occurred_at", record.occurredAtSec());
            row.addProperty("summary", safe(record.summary(), 2000));
            row.addProperty("resolution", safe(record.resolution(), 8000));
            row.addProperty("full_text", safe(record.fullText(), 10000));
            row.add("dense_vector", toJsonArray(dense));
            // sparse_vector 由 BM25 Function 自动生成,不需要手动填

            UpsertResp resp = client.upsert(UpsertReq.builder()
                    .collectionName(collection)
                    .data(Collections.singletonList(row))
                    .build());
            log.info("[Milvus] upsert 成功 id={} affected={}", record.id(), resp.getUpsertCnt());
        } catch (Exception e) {
            log.error("[Milvus] upsert 失败 id={}: {}", record.id(), e.getMessage(), e);
        }
    }

    /** 混合检索: BM25(text) + dense(embedding) + RRF rerank */
    public List<Map<String, Object>> hybridSearch(String queryText, String serviceName, String errorClass) {
        try {
            float[] dense = embed(queryText);

            // 元数据过滤: 只看相同 service,但 error_class 不限(允许跨异常类找到相似根因)
            StringBuilder expr = new StringBuilder();
            if (serviceName != null && !serviceName.isBlank()) {
                expr.append("service_name == \"").append(escape(serviceName)).append("\"");
            }
            // 时间衰减: 只查最近 180 天的
            long now = System.currentTimeMillis() / 1000;
            long cutoff = now - 180L * 86400;
            if (expr.length() > 0) expr.append(" AND ");
            expr.append("occurred_at >= ").append(cutoff);

            AnnSearchReq bm25Req = AnnSearchReq.builder()
                    .vectorFieldName("sparse_vector")
                    .vectors(Collections.singletonList(new EmbeddedText(queryText)))
                    .topK(topK * 3)
                    .expr(expr.toString())
                    .params("{}")
                    .build();

            AnnSearchReq denseReq = AnnSearchReq.builder()
                    .vectorFieldName("dense_vector")
                    .vectors(Collections.singletonList(new FloatVec(dense)))
                    .topK(topK * 3)
                    .expr(expr.toString())
                    .params("{}")
                    .metricType(IndexParam.MetricType.COSINE)
                    .build();

            HybridSearchReq req = HybridSearchReq.builder()
                    .collectionName(collection)
                    .searchRequests(Arrays.asList(bm25Req, denseReq))
                    .ranker(new RRFRanker(60))
                    .topK(topK)
                    .outFields(Arrays.asList("id", "service_name", "error_class",
                            "severity", "summary", "resolution", "occurred_at"))
                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                    .build();

            SearchResp resp = client.hybridSearch(req);
            List<Map<String, Object>> out = new ArrayList<>();
            if (resp.getSearchResults() != null && !resp.getSearchResults().isEmpty()) {
                for (SearchResp.SearchResult sr : resp.getSearchResults().get(0)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("score", sr.getScore());
                    item.put("entity", sr.getEntity());
                    out.add(item);
                }
            }
            log.info("[Milvus] hybrid search 返回 {} 条 (query={}, service={})",
                    out.size(), truncate(queryText, 80), serviceName);
            return out;
        } catch (Exception e) {
            log.error("[Milvus] hybrid search 失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private float[] embed(String text) {
        if (embeddingModel == null) {
            return new float[1536];
        }
        return embeddingModel.embed(text == null ? "" : text);
    }

    private com.google.gson.JsonArray toJsonArray(float[] arr) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (float v : arr) a.add(v);
        return a;
    }

    private String safe(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
