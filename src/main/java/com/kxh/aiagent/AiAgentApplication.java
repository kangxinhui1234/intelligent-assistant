package com.kxh.aiagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

//@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
@SpringBootApplication
@MapperScan("com.kxh.aiagent.mapper")
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }



}
