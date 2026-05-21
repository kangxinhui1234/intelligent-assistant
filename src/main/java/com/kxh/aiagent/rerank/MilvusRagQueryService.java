package com.kxh.aiagent.rerank;

import com.alibaba.cloud.ai.model.RerankModel;
import com.kxh.aiagent.model.RagResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@Profile("milvus")
public class MilvusRagQueryService implements RagService {

    @Resource(name = "vectorStore")
    VectorStore vectorStore;

    @Resource
    ChatModel dashscopeChatModel;

    @Resource
    RerankModel dashScopeRerankModel;

    @Override
    public RagResponse rerankRag(String message) {
        SearchRequest searchRequest = SearchRequest.builder().topK(10).similarityThreshold(0.3).build();

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel).defaultAdvisors(
                new MultiRetrievalRerankAdvisor(vectorStore, dashScopeRerankModel, searchRequest)
        ).build();

        ChatResponse chatResponse = chatClient.prompt().user(message).call().chatResponse();
        List<Document> docs = chatResponse.getMetadata().get("qa_retrieved_documents");

        List<String> contexts = docs.stream()
                .map(Document::getText)
                .collect(Collectors.toList());

        String answer = chatResponse.getResult().getOutput().getText();

        return RagResponse.builder()
                .question(message)
                .contexts(String.join(",", contexts))
                .answer(answer)
                .search_type("milvus_dense_rerank")
                .build();
    }
}
