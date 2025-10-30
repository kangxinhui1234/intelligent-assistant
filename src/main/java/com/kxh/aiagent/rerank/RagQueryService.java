package com.kxh.aiagent.rerank;


import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.alibaba.cloud.ai.model.RerankModel;
import com.kxh.aiagent.model.RagResponse;
import com.kxh.aiagent.ragas.RagasEvaluationRequest;
import com.kxh.aiagent.ragas.RagasEvaluationResult;
import com.kxh.aiagent.ragas.RagasEvaluationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RagQueryService {

    @Resource
    VectorStore elasticSearchVectorStore;

    @Resource
    ElasticsearchSparseRetriever elasticsearchSparseRetriever;
   // private final ChatClient chatClient;
    @Resource
    ChatModel dashscopeChatModel;

    @Resource
    RerankModel dashScopeRerankModel;

    @Autowired
    RagasEvaluationService ragasEvaluationService;


    public RagResponse rerankRag(String message){

        SearchRequest searchRequest =  SearchRequest.builder().topK(5).similarityThreshold(0.3).build();
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel).defaultAdvisors(
                //  new RetrievalRerankAdvisor(vectorStore, dashScopeRerankModel) // 重排模型
                new MultiRetrievalRerankAdvisor(elasticSearchVectorStore,elasticsearchSparseRetriever,dashScopeRerankModel, searchRequest)

        ).build();
        ChatResponse chatResponse =  chatClient.prompt().user(message).call().chatResponse();
        List<Document> ls = chatResponse.getMetadata().get("qa_retrieved_documents");

        List<String>  lsStr = ls.stream().map(documet -> documet.getText())
                .collect(Collectors.toList());

        String answer = chatResponse.getResult().getOutput().getText();
        RagResponse ragResponse =  RagResponse.builder().question(message)
                .contexts(String.join(",", lsStr))
                        .answer(answer)
                                .build();

        // 苹果生成结果
       /* RagasEvaluationResult ragasEvaluationResult = ragasEvaluationService.evaluateWithRagas(RagasEvaluationRequest.builder()
                .question(message)
                .contexts(lsStr)
                .answer(chatResponse.getResult().getOutput().getText())
                .build());*/

        log.info(answer);
        return ragResponse;
    }


}
