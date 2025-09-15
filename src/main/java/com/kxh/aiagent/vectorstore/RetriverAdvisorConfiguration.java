package com.kxh.aiagent.vectorstore;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClientBuilder;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class RetriverAdvisorConfiguration {

    @Autowired
    VectorStore pgSqlVectorStore;

    @Autowired
    private ChatModel dashscopeChatModel;
    /**
     * pgSql RAG检索增强器
     */
    @Bean("pgSqlRetriverAdvisor")
    public Advisor pgSqlRetriverAdvisor(){
        ChatClient.Builder chatClientBuilder = ChatClient.builder(dashscopeChatModel)  ;
        // 3. 配置查询重写转换器
        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(
                        chatClientBuilder.build().mutate()
                )
                .build();

        // 4. 配置查询增强器（允许空上下文）
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true) // 允许知识库为空
               .emptyContextPromptTemplate(new PromptTemplate("抱歉我只能回答理财相关问题，如果有疑问，可以联系客服电话632584")) // 自定义空模板返回消息
                .build();

        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(pgSqlVectorStore)
                .similarityThreshold(0.6)
                .topK(5)
              //  .filterExpression(new FilterExpressionBuilder().eq("")) 元数据筛选表达式
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer) // 重写查询转换器
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter) // 允许知识库中为空 并且可以自定义空模板返回
                .build();
    }
}
