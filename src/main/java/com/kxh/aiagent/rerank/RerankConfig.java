package com.kxh.aiagent.rerank;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankConfig {

    /**
     * private String model = "gte-rerank"; 阿里默认的重排序模型
     * @param dashScopeApi
     * @return
     */
    @Bean
    public RerankModel dashScopeRerankModel(DashScopeApi dashScopeApi) {
        return new DashScopeRerankModel(dashScopeApi);  // 声明重排序模型Bean
    }
}