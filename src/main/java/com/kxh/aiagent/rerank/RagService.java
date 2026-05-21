package com.kxh.aiagent.rerank;

import com.kxh.aiagent.model.RagResponse;

public interface RagService {
    RagResponse rerankRag(String message);
}
