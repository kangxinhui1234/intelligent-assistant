package com.kxh.aiagent.vectorstore;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class BatchStrategyConfiguration {

    /**
     * 嵌入策略  设置单个分片文档的最大token数量
     * @return
     */
    @Bean
    public BatchingStrategy customTokenCountBatchingStrategy() {
        return new TokenCountBatchingStrategy(
                EncodingType.CL100K_BASE,  // 指定编码类型
                8000,                      // 设置最大输入标记计数
                0.1                        // 设置保留百分比
        );

    }

}
