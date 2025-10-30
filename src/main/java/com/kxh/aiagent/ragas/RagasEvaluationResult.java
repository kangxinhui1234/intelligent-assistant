package com.kxh.aiagent.ragas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 自定义的响应DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagasEvaluationResult {
    private Double faithfulness;
    private Double answerRelevance;
    private Double contextPrecision;
    private Double contextRecall;
    private Double overallScore;

    // 添加一些实用方法
    public boolean isValid() {
        return faithfulness != null && answerRelevance != null;
    }

    public double getSafeOverallScore() {
        return overallScore != null ? overallScore : 0.0;
    }
}