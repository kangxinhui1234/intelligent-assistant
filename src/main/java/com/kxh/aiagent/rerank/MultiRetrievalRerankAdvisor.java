package com.kxh.aiagent.rerank;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankOptions;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiRetrievalRerankAdvisor implements BaseAdvisor {

    private static final double RRF_K = 60; // RRF算法参数

    private final VectorStore vectorStore;
    private final SparseRetriever sparseRetriever;
    private final RerankModel rerankModel;
    private final SearchRequest searchRequest;

    /**
     * RAG优化：
     * 1、对回答内容指示出来源
     * 2、回答兜底，如果没有文档，返回兜底话术
     * 3、测试RAG评估： 准备一组数据集合，标注预期数据结果，然后输入RAG生成，检查实际生成的内容是否符合预期，使用工具评估差异
     */
    // 定义 PromptTemplate
    PromptTemplate promptTemplate = PromptTemplate.builder()
            .template("""
                你是一个智能问答助手。
                请基于以下文档内容回答用户的问题。如果文档中没有答案，请提示无法回答相关内容。
                对于回答所引用的内容要指出数据来源。
                文档内容：
                {question_answer_context}

                用户问题：
                {query}

                请提供详细、准确、专业的回答：
                """)
            .build();

    public MultiRetrievalRerankAdvisor(
            VectorStore vectorStore,
            SparseRetriever sparseRetriever,
            RerankModel rerankModel,
            SearchRequest searchRequest
    ) {
        this.vectorStore = vectorStore;
        this.sparseRetriever = sparseRetriever;
        this.rerankModel = rerankModel;
        this.searchRequest = searchRequest;
    }

    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        Map<String, Object> context = request.context();
        UserMessage userMessage = request.prompt().getUserMessage();
        String query = userMessage.getText();

        // 1. 并行执行两种检索
        List<Document> denseDocs = vectorStore.similaritySearch(
                SearchRequest.from(searchRequest)
                        .query(query)
                       // .filterExpression("app=love")
                        .topK(5)
                        .build()
        );

        List<Document> sparseDocs = null;
        try {
            sparseDocs = sparseRetriever.retrieve(query, searchRequest.getTopK());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 2. 结果融合 (RRF算法)
        List<Document> fusedDocs = fuseResults(denseDocs, sparseDocs);

        // 3. 保存原始结果用于调试
        context.put("dense_retrieved_documents", denseDocs);
        context.put("sparse_retrieved_documents", sparseDocs);
        context.put("fused_documents", fusedDocs);

        // 4. 重排序
        List<Document> rerankedDocs = doRerank(request, fusedDocs);
        context.put("qa_retrieved_documents", rerankedDocs);

        // 5. 构建上下文
        String documentContext = rerankedDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));

        String augmentedUserText = promptTemplate.render(Map.of(
                "query", query,
                "question_answer_context", documentContext
        ));

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(augmentedUserText))
                .context(context)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        ChatResponse.Builder chatResponseBuilder;
        if (chatClientResponse.chatResponse() == null) {
            chatResponseBuilder = ChatResponse.builder();
        } else {
            chatResponseBuilder = ChatResponse.builder().from(chatClientResponse.chatResponse());
        }

        chatResponseBuilder.metadata("qa_retrieved_documents", chatClientResponse.context().get("qa_retrieved_documents"));
        return ChatClientResponse.builder().chatResponse(chatResponseBuilder.build()).context(chatClientResponse.context()).build();

    }

    // RRF融合算法实现
    private List<Document> fuseResults(List<Document> list1, List<Document> list2) {
        // 创建文档到排名的映射
        Map<String, Double> docScores = new HashMap<>();

        // 处理第一个列表
        for (int i = 0; i < list1.size(); i++) {
            Document doc = list1.get(i);
            String docId = generateDocId(doc);
            double score = 1.0 / (RRF_K + i + 1);
            docScores.put(docId, docScores.getOrDefault(docId, 0.0) + score);
        }

        // 处理第二个列表
        for (int i = 0; i < list2.size(); i++) {
            Document doc = list2.get(i);
            String docId = generateDocId(doc);
            double score = 1.0 / (RRF_K + i + 1);
            docScores.put(docId, docScores.getOrDefault(docId, 0.0) + score);
        }

        // 按融合分数排序
        return Stream.concat(list1.stream(), list2.stream())
                .distinct()
                .sorted((d1, d2) ->
                        Double.compare(
                                docScores.getOrDefault(generateDocId(d2), 0.0),
                                docScores.getOrDefault(generateDocId(d1), 0.0)
                        )
                )
                .collect(Collectors.toList());
    }

    private String generateDocId(Document doc) {
        // 使用内容哈希作为文档ID，实际应用中可用数据库ID
        return DigestUtils.md5DigestAsHex(doc.getText().getBytes());
    }

    private List<Document> doRerank(ChatClientRequest request, List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return documents;
        } else {
            RerankOptions options = DashScopeRerankOptions.builder().withTopN(10).build();
            RerankRequest rerankRequest = new RerankRequest(request.prompt().getUserMessage().getText(), documents,options);
            RerankResponse response = this.rerankModel.call(rerankRequest);
            return response != null && response.getResults() != null ? (List)response.getResults().stream().filter((doc) -> {
                return doc != null && doc.getScore() >0;
            }).sorted(Comparator.comparingDouble(DocumentWithScore::getScore).reversed()).map(DocumentWithScore::getOutput).collect(Collectors.toList()) : documents;
        }

    }

    @Override
    public String getName() {
        return "混合检索器";
    }

    @Override
    public int getOrder() {
        return 0;
    }

}