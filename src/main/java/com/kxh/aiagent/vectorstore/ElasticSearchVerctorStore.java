package com.kxh.aiagent.vectorstore;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import jakarta.annotation.Resource;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;



@Component
public class ElasticSearchVerctorStore {

    @Resource
    EmbeddingModel dashScopeEmbeddingModel;

    @Autowired
    RestClient restClient;
    @Bean
    public VectorStore elasticSearchVectorStore(RestClient restClient) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName("my-pdf-index");    // Optional: defaults to "spring-ai-document-index"
        options.setSimilarity(SimilarityFunction.cosine);           // Optional: defaults to COSINE
        options.setDimensions(1536);             // Optional: defaults to model dimensions or 1536

        return ElasticsearchVectorStore.builder(restClient, dashScopeEmbeddingModel)
                .options(options)                     // Optional: use custom options
                .initializeSchema(true)               // Optional: defaults to false
                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
                .build();
    }


}
