package com.kxh.aiagent.rerank;

import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;

public interface SparseRetriever {
    List<Document> retrieve(String query, int topK) throws IOException;
}