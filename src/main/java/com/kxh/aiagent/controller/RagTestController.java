package com.kxh.aiagent.controller;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.kxh.aiagent.model.RagRequest;
import com.kxh.aiagent.model.RagResponse;
import com.kxh.aiagent.rerank.MultiRetrievalRerankAdvisor;
import com.kxh.aiagent.rerank.RagQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagTestController {

    @Autowired
    RagQueryService ragQueryService;

  //  @PostMapping("/search/vector")
//    public SearchResponse vectorSearch(@RequestBody SearchRequest request) {
//        // åéæ£ç´¢å®ç°
//    }

  //  @PostMapping("/search/bm25")
//    public SearchResponse bm25Search(@RequestBody SearchRequest request) {
//        // BM25 æ£ç´¢å®ç°
//    }

    @PostMapping("/qa")
    public RagResponse ragQuery(@RequestBody RagRequest request) {
       return  ragQueryService.rerankRag(request.getQuestion());
    }
}
