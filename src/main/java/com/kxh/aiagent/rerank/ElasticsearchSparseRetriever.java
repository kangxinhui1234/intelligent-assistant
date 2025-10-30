package com.kxh.aiagent.rerank;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.stereotype.Component;

import javax.print.Doc;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 基于Elasticsearch的稀疏检索实现
@Component
public class ElasticsearchSparseRetriever implements SparseRetriever {

    private final ElasticsearchClient esClient;

    public ElasticsearchSparseRetriever(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public List<Document> retrieve(String query, int topK) throws IOException {


        // 使用 Elasticsearch Java Client 的 lambda builder 来执行搜索
        SearchResponse<Map> response = esClient.search(s -> s
                        .index("pdf_docs")
                        .size(topK)
                        .query(q -> q
                                .match(m -> m
                                        .field("content")
                                        .query(query)
                                )
                        ),
                Map.class);

        // 将 hits 映射为 List<Document>
        List<Document> results = response.hits().hits().stream()
                .map((Hit<Map> hit) -> {
                    Map<String, Object> src = hit.source();
                    String content = src == null ? "" : (String) src.getOrDefault("content", "");
                    Document doc = new Document(content);
                    // 元数据：来源、score、其他原字段
                    Map<String, Object> metadata = doc.getMetadata();
                    metadata.put("source", "es");
                    metadata.put("score", hit.score());
                    // 把原始字段也放进 metadata（可选）
                    if (src != null) {
                        metadata.putAll(src);
                    }
                    Document docNew  = Document.builder().text(content).metadata(metadata).score(hit.score()).build();
                    return docNew;
                })
                .collect(Collectors.toList());

        return results;
    }
}
