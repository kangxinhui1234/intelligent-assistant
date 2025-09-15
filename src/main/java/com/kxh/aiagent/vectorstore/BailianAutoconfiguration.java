package com.kxh.aiagent.vectorstore;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

@Configuration
public class BailianAutoconfiguration {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /**
     * 百炼调用时需要配置 DashScope API，对 dashScopeApi 强依赖。
     * @return
     */
    @Bean
    public DashScopeApi dashScopeApi() {

        return DashScopeApi.builder().apiKey(apiKey).build();
    }

}
