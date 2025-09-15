package com.kxh.aiagent.vectorstore;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import com.knuddels.jtokkit.api.EncodingType;
import com.kxh.aiagent.etl.MyMarkdownReader;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class MySimpleVectorStore  {
    @Autowired
    MyMarkdownReader reader;

    @Autowired
    BatchingStrategy batchingStrategy;

    @Bean
    public VectorStore simpleVectorStore(@Qualifier("dashscopeEmbeddingModel")EmbeddingModel dashScopeEmbeddingModel) throws IOException {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashScopeEmbeddingModel)
                .build();
        simpleVectorStore.doAdd(reader.loadMarkdown());
        return simpleVectorStore;
    }



        @Bean
        public Advisor loveAppRagCloudAdvisor(@Autowired DashScopeApi dashScopeApi) {
            final String KNOWLEDGE_INDEX = "投资理财知识库";
            DocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                    DashScopeDocumentRetrieverOptions.builder()
                            .withIndexName(KNOWLEDGE_INDEX)
                            .withRerankTopN(2)
                            .withRerankMinScore(0.5f)
                            .build());
            return RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(documentRetriever)
                    .build();
        }

    /**
     * PGSql向量数据库实现类VectorStore
      * @return
     */
   // @Bean("pgSqlVectorStore")
    public VectorStore pgSqlVectorStore(JdbcTemplate jdbcTemplate, @Qualifier("dashscopeEmbeddingModel")EmbeddingModel dashscopeEmbeddingModel) throws IOException {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                    .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                    .distanceType(COSINE_DISTANCE)       // Optional:指定向量相似度计算方式 COSINE_DISTANCE: 余弦距离（最常用）
                    .indexType(HNSW)                     // Optional: defaults to HNSW  指定向量索引类型 HNSW: 分层可导航小世界算法（高性能）
                    .initializeSchema(true)              // Optional: defaults to false 自动创建向量存储表
                    .schemaName("public")                // Optional: defaults to "public"
                    .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                    .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                /**
                 *     含义：指定批量插入文档时的最大批次大小（即一次批量操作最多处理的文档数量）
                 *     默认值：如果不设置，默认是10000。
                 *     注意：较大的批次可以提高插入效率，但需要更多内存。如果遇到内存问题，可以适当调小。
                 */
                .batchingStrategy(batchingStrategy) // 文档存入向量数据库单个最大tokens控制策略
                    .build();

        vectorStore.add(reader.loadMarkdown()); // 初始化加载所有数据

        return vectorStore;

    }



}
