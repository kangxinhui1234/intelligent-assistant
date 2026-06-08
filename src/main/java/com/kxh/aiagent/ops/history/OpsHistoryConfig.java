package com.kxh.aiagent.ops.history;

import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Milvus 历史故障知识库初始化。
 * <p>
 * 重要: @PostConstruct 中直接 new MilvusClientV2,避免调用 @Bean 方法触发 CGLIB 循环引用。
 */
@Configuration
@ConditionalOnProperty(name = "ops.history.enabled", havingValue = "true", matchIfMissing = false)
public class OpsHistoryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpsHistoryConfig.class);

    @Value("${ops.history.milvus.host:127.0.0.1}") private String host;
    @Value("${ops.history.milvus.port:19530}") private int port;
    @Value("${ops.history.milvus.collection:ops_incident_kb}") private String collection;
    @Value("${ops.history.embedding-dim:1536}") private int dim;

    private MilvusClientV2 client;

    @PostConstruct
    public void init() {
        try {
            ConnectConfig cfg = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .build();
            this.client = new MilvusClientV2(cfg);
            log.info("[Milvus] 客户端已连接: http://{}:{}", host, port);

            boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .collectionName(collection).build());
            if (exists) {
                log.info("[Milvus] Collection 已存在: {}", collection);
                return;
            }
            createCollection();
            log.info("[Milvus] Collection 创建成功: {}", collection);
        } catch (Exception e) {
            log.error("[Milvus] Collection 初始化失败: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    @Bean
    public MilvusClientV2 opsMilvusClient() {
        return this.client;
    }

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .build();

        schema.addField(AddFieldReq.builder().fieldName("id").dataType(DataType.VarChar)
                .maxLength(64).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("service_name").dataType(DataType.VarChar)
                .maxLength(128).build());
        schema.addField(AddFieldReq.builder().fieldName("error_class").dataType(DataType.VarChar)
                .maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("severity").dataType(DataType.VarChar)
                .maxLength(16).build());
        schema.addField(AddFieldReq.builder().fieldName("occurred_at").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("summary").dataType(DataType.VarChar)
                .maxLength(2000).build());
        schema.addField(AddFieldReq.builder().fieldName("resolution").dataType(DataType.VarChar)
                .maxLength(8000).build());

        Map<String, Object> analyzerParams = new HashMap<>();
        analyzerParams.put("tokenizer", "jieba");
        schema.addField(AddFieldReq.builder().fieldName("full_text").dataType(DataType.VarChar)
                .maxLength(10000)
                .enableAnalyzer(true)
                .analyzerParams(analyzerParams)
                .build());
        schema.addField(AddFieldReq.builder().fieldName("sparse_vector")
                .dataType(DataType.SparseFloatVector).build());
        schema.addField(AddFieldReq.builder().fieldName("dense_vector")
                .dataType(DataType.FloatVector).dimension(dim).build());

        CreateCollectionReq.Function bm25Fn = CreateCollectionReq.Function.builder()
                .name("bm25_text2sparse")
                .description("Auto generate sparse vector from full_text via BM25")
                .functionType(FunctionType.BM25)
                .inputFieldNames(Collections.singletonList("full_text"))
                .outputFieldNames(Collections.singletonList("sparse_vector"))
                .build();
        schema.addFunction(bm25Fn);

        IndexParam sparseIdx = IndexParam.builder()
                .fieldName("sparse_vector")
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .extraParams(Map.of("bm25_k1", 1.2, "bm25_b", 0.75))
                .build();
        IndexParam denseIdx = IndexParam.builder()
                .fieldName("dense_vector")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        CreateCollectionReq req = CreateCollectionReq.builder()
                .collectionName(collection)
                .collectionSchema(schema)
                .indexParams(Arrays.asList(sparseIdx, denseIdx))
                .build();
        client.createCollection(req);
    }
}
