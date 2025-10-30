package com.kxh.aiagent.ragas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 自定义的请求DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagasEvaluationRequest {
    private String question;
    private String answer;
    private List<String> contexts;
    private String groundTruth;
    private List<String> groundTruthContexts; // 可选字段
}