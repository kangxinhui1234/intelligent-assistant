package com.kxh.aiagent.ops.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

/**
 * Redis Stream 配置 — Webhook / 主动巡检入口都走这条 MQ,解耦"接告警"与"跑流水线"。
 * <p>
 * Key 设计:
 *   - {@link #STREAM_KEY}    "ops:alerts:stream"   主流
 *   - {@link #DLQ_KEY}       "ops:alerts:dlq"      死信队列
 *   - {@link #CONSUMER_GROUP} "ops-incident-group"  消费组(支持多副本水平扩展)
 * <p>
 * 配置项:
 *   - ops.mq.enabled        (默认 true) 总开关,关掉退化到直连 processor
 *   - ops.mq.consumer-name  (默认 host-pid) 消费者名,用于 XREADGROUP 区分实例
 *   - ops.mq.batch-size     (默认 1)   单次拉取消息数
 *   - ops.mq.poll-timeout-ms(默认 2000) XREADGROUP block 超时
 */
@Configuration
public class IncidentStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(IncidentStreamConfig.class);

    public static final String STREAM_KEY     = "ops:alerts:stream";
    public static final String DLQ_KEY        = "ops:alerts:dlq";
    public static final String CONSUMER_GROUP = "ops-incident-group";

    @Value("${ops.mq.batch-size:1}")
    private int batchSize;

    @Value("${ops.mq.poll-timeout-ms:2000}")
    private long pollTimeoutMs;

    /**
     * 启动时确保 consumer group 存在 (幂等)。
     * 不存在时创建,已存在时忽略 BUSYGROUP 错误。
     */
    @Bean
    public StreamGroupInitializer streamGroupInitializer(StringRedisTemplate redis) {
        return new StreamGroupInitializer(redis, STREAM_KEY, CONSUMER_GROUP);
    }

    /**
     * 消息监听容器 — Spring Data Redis 提供的高层抽象。
     * 内部封装 XREADGROUP + 长轮询 + 失败重试。
     * 实际消费逻辑在 {@link IncidentStreamConsumer#onMessage}。
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
            RedisConnectionFactory connectionFactory,
            IncidentStreamConsumer consumer,
            StreamGroupInitializer initializer) {

        initializer.ensure();   // 必须先建组,否则 listener 启动会失败

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .batchSize(batchSize)
                        .pollTimeout(Duration.ofMillis(pollTimeoutMs))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                org.springframework.data.redis.connection.stream.Consumer.from(CONSUMER_GROUP, consumer.consumerName()),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                consumer);

        log.info("[Stream] 监听容器已启动 stream={} group={} consumer={}",
                STREAM_KEY, CONSUMER_GROUP, consumer.consumerName());
        return container;
    }

    /** 确保 consumer group 存在 — 幂等 */
    public static class StreamGroupInitializer {
        private final StringRedisTemplate redis;
        private final String streamKey;
        private final String groupName;

        public StreamGroupInitializer(StringRedisTemplate redis, String streamKey, String groupName) {
            this.redis = redis;
            this.streamKey = streamKey;
            this.groupName = groupName;
        }

        public void ensure() {
            try {
                redis.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
                log.info("[Stream] 已创建 consumer group: {} on {}", groupName, streamKey);
            } catch (Exception e) {
                // BUSYGROUP 表示已存在,正常忽略
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    log.debug("[Stream] consumer group {} 已存在", groupName);
                } else {
                    log.warn("[Stream] 创建 consumer group 失败 (可能 stream 不存在,首次 XADD 时会自动建): {}", e.getMessage());
                }
            }
        }
    }
}
