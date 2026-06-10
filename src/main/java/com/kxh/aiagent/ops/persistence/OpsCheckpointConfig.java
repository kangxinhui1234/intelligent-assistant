package com.kxh.aiagent.ops.persistence;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.state.AgentStateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Agent 流水线 CheckPointer — 状态持久化到 MySQL,支持宕机续跑。
 * <p>
 * 配置项: ops.checkpoint.enabled (默认 true) — 关掉退化到 MemorySaver
 * <p>
 * 表结构由 MysqlSaver 自己建 (CREATE_IF_NOT_EXISTS):
 *   - ai_agent_thread       (thread_id, ...)        — 线程级元数据
 *   - ai_agent_checkpoint   (thread_id, ..., state) — 每个 Stage 完成后的状态快照
 */
@Configuration
public class OpsCheckpointConfig {

    private static final Logger log = LoggerFactory.getLogger(OpsCheckpointConfig.class);

    @Value("${ops.checkpoint.enabled:true}")
    private boolean enabled;

    /**
     * 全局共享的 CheckPointSaver Bean — OpsAgentConfig.opsIncidentPipeline 注入。
     * 优先级:
     *  1) ops.checkpoint.enabled=true 且 DataSource 可用 → MysqlSaver (持久化)
     *  2) 否则 → MemorySaver (无持久化,JVM 重启数据丢失)
     */
    @Bean
    public BaseCheckpointSaver opsCheckpointSaver(DataSource dataSource) {
        if (!enabled) {
            log.warn("[Checkpoint] ops.checkpoint.enabled=false → 使用 MemorySaver, 状态不持久化");
            return MemorySaver.builder().build();
        }
        try {
            AgentStateFactory<OverAllState> stateFactory = OverAllState::new;
            StateSerializer serializer = new SpringAIJacksonStateSerializer(stateFactory);
            MysqlSaver saver = MysqlSaver.builder()
                    .dataSource(dataSource)
                    .stateSerializer(serializer)
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .build();
            log.info("[Checkpoint] MysqlSaver 已启用 — 流水线状态将持久化到 MySQL, 支持宕机续跑");
            return saver;
        } catch (Throwable e) {
            log.error("[Checkpoint] MysqlSaver 初始化失败,退化到 MemorySaver: {}", e.getMessage(), e);
            return MemorySaver.builder().build();
        }
    }
}
