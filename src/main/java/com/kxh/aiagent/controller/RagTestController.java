package com.kxh.aiagent.controller;

import com.kxh.aiagent.model.RagRequest;
import com.kxh.aiagent.model.RagResponse;
import com.kxh.aiagent.rerank.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagTestController {

    @Autowired
    RagService ragService;

    @PostMapping("/qa")
    public RagResponse ragQuery(@RequestBody RagRequest request) {
        return ragService.rerankRag(request.getQuestion());
    }
}
