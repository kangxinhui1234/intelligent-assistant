package com.kxh.aiagent.ragas;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagasEvaluationService {

    private final RestTemplate restTemplate;
    private final String ragasServiceUrl = "http://localhost:8000";

    public RagasEvaluationService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 调用RAGAS评估服务
     */
    public RagasEvaluationResult evaluateWithRagas(RagasEvaluationRequest request) {
        try {
            String evaluateUrl = ragasServiceUrl + "/evaluate";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RagasEvaluationRequest> entity = new HttpEntity<>(request, headers);
            log.info(new ObjectMapper().writeValueAsString(request));
            ResponseEntity<RagasEvaluationResult> response = restTemplate.exchange(
                    evaluateUrl,
                    HttpMethod.POST,
                    entity,
                    RagasEvaluationResult.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("RAGAS服务返回异常状态: {}", response.getStatusCode());
                return createFallbackResult();
            }

        } catch (Exception e) {
            log.error("调用RAGAS评估服务失败: {}", e.getMessage());
            return createFallbackResult();
        }
    }

    /**
     * 批量评估
     */
    public List<RagasEvaluationResult> batchEvaluate(List<RagasEvaluationRequest> requests) {
        return requests.parallelStream()
                .map(this::evaluateWithRagas)
                .collect(Collectors.toList());
    }

    /**
     * 降级结果
     */
    private RagasEvaluationResult createFallbackResult() {
        return RagasEvaluationResult.builder()
                .faithfulness(0.0)
                .answerRelevance(0.0)
                .contextPrecision(0.0)
                .contextRecall(0.0)
                .overallScore(0.0)
                .build();
    }
}