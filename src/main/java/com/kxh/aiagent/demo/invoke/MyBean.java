package com.kxh.aiagent.demo.invoke;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.kxh.aiagent.chatmemory.KryoRedisSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class MyBean {
    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        if (redisConnectionFactory == null) {
            log.error("RedisConnectionFactory 为 null！无法创建 RedisTemplate");
            throw new IllegalStateException("RedisConnectionFactory 不可用");
        }
        RedisTemplate template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        // 使用 StringRedisSerializer 来序列化和反序列化 redis 的 key 值
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        // 使用 GenericJackson2JsonRedisSerializer 来序列化和反序列化 redis 的 value 值
        KryoRedisSerializer serializer = new KryoRedisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }


    // 容器初始化完成后打印所有 RedisTemplate Bean
    @Bean
    public ApplicationRunner redisTemplateChecker(ApplicationContext context) {
        return args -> {
            String[] beanNames = context.getBeanNamesForType(RedisTemplate.class);
            log.info("📌 当前容器中 RedisTemplate Bean 数量: {}", beanNames.length);
            for (String name : beanNames) {
                Object bean = context.getBean(name);
                log.info("   - Bean 名称: {}, 类型: {}", name, bean.getClass().getName());
            }
        };
    }

}
