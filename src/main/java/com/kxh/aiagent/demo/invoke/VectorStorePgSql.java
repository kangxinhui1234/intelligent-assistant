package com.kxh.aiagent.demo.invoke;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.swing.plaf.basic.BasicComboBoxUI;
import java.util.List;
import java.util.Map;
//@Component
public class VectorStorePgSql implements CommandLineRunner {
    @Autowired
    VectorStore pgSqlVectorStore;

    @Override
    public void run(String... args) throws Exception {
//        List<Document> documents = List.of(
//                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
//                new Document("The World is Big and Salvation Lurks Around the Corner"),
//                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));

// Add the documents to PGVector
       // pgSqlVectorStore.add(documents);

        SearchRequest request = SearchRequest.builder()
                .query("小白怎么投资？")
                .topK(5)                  // 返回最相似的5个结果
                .similarityThreshold(0.5) // 相似度阈值，0.0-1.0之间
                //.filterExpression("excerpt_keywords == 'Dividend growth strategy'")  // 过滤表达式
                .build();


        List<Document> results = pgSqlVectorStore.similaritySearch(request);


       // List<Document> results = this.pgSqlVectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

    }
}
