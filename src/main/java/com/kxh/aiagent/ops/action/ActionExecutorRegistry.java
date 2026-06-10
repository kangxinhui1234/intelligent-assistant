package com.kxh.aiagent.ops.action;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ActionExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutorRegistry.class);

    private final Map<ActionType, ActionExecutor> byType = new EnumMap<>(ActionType.class);
    private final List<ActionExecutor> executors;

    public ActionExecutorRegistry(List<ActionExecutor> executors) {
        this.executors = executors;
    }

    @PostConstruct
    void register() {
        for (ActionExecutor e : executors) {
            ActionExecutor prev = byType.put(e.type(), e);
            if (prev != null) {
                log.warn("ActionType={} 有多个实现, 后注册的覆盖: {} -> {}",
                        e.type(), prev.getClass().getSimpleName(), e.getClass().getSimpleName());
            }
        }
        log.info("[ActionExecutor] 已注册 {} 个执行器: {}", byType.size(), byType.keySet());
    }

    public ActionExecutor get(ActionType type) {
        return byType.get(type);
    }
}
