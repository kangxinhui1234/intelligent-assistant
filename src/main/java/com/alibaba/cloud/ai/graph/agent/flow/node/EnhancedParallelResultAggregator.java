package com.alibaba.cloud.ai.graph.agent.flow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * state.value(outputKey).get()  →  GraphResponse
 *                                     │
 *                     graphResponse.resultValue()
 *                                     │
 *                     ┌────────┴────────┐
 *                     │ isPresent() == true           │ isPresent() == false
 *                     │ && value instanceof Map       │
 *                     ↓                               ↓
 *               ✅ 走 happy path               💥 Optional.get() 崩溃
 *               也就是：某个 Agent 的 GraphResponse.resultValue 是空的，导致走进了 buggy 的 else 分支。
 */
public class EnhancedParallelResultAggregator implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedParallelResultAggregator.class);

    private final String outputKey;
    private final java.util.List<BaseAgent> subAgents;
    private final Object mergeStrategy;
    private final Integer maxConcurrency;

    public EnhancedParallelResultAggregator(String outputKey,
                                             java.util.List<BaseAgent> subAgents,
                                             Object mergeStrategy,
                                             Integer maxConcurrency) {
        this.outputKey = outputKey;
        this.subAgents = subAgents;
        this.mergeStrategy = mergeStrategy != null ? mergeStrategy : KeyStrategy.REPLACE;
        this.maxConcurrency = maxConcurrency;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("[FIXED] EnhancedParallelResultAggregator loaded from project classpath, sub-agents: {}",
                subAgents.size());
        logger.info("[STATE] Current state keys: {}, messages count: {}",
                state.data().keySet(),
                state.value("messages").map(m -> {
                    if (m instanceof java.util.List) return ((java.util.List<?>) m).size();
                    return -1;
                }).orElse(-1));

        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> parallelResults = new HashMap<>();

        for (BaseAgent subAgent : subAgents) {
            String agentOutputKey = subAgent.getOutputKey();
            if (agentOutputKey == null) {
                continue;
            }

            Optional<Object> valueOpt = state.value(agentOutputKey);
            if (valueOpt.isPresent()) {
                Object value = valueOpt.get();

                if (value instanceof GraphResponse) {
                    GraphResponse graphResponse = (GraphResponse) value;
                    Optional<Object> resultValue = graphResponse.resultValue();

                    logger.info("[DIAG] {} GraphResponse: isDone={}, isError={}, resultValue.isPresent={}, " +
                            "resultValueType={}, metadata={}",
                            subAgent.name(),
                            graphResponse.isDone(),
                            graphResponse.isError(),
                            resultValue.isPresent(),
                            resultValue.map(Object::getClass).map(Class::getSimpleName).orElse("N/A"),
                            graphResponse.getAllMetadata());

                    if (resultValue.isPresent() && resultValue.get() instanceof Map) {
                        Map<?, ?> innerMap = (Map<?, ?>) resultValue.get();
                        parallelResults.put(agentOutputKey, innerMap.get(agentOutputKey));
                    } else {
                        // BUGFIX: original code called graphResponse.resultValue().get()
                        // without checking isPresent(), causing NoSuchElementException
                        // when resultValue was empty
                        parallelResults.put(agentOutputKey,
                                resultValue.isPresent() ? resultValue.get() : graphResponse);
                    }
                } else {
                    parallelResults.put(agentOutputKey, value);
                }

                logger.info("Collected result from {}: key={}, type={}",
                        subAgent.name(), agentOutputKey,
                        value == null ? "null" : value.getClass().getSimpleName());
            } else {
                logger.warn("No output found for sub-agent: {} (outputKey: {}), state keys: {}",
                        subAgent.name(), agentOutputKey, state.data().keySet());
            }
        }

        Object merged;
        if (mergeStrategy instanceof ParallelAgent.MergeStrategy) {
            merged = ((ParallelAgent.MergeStrategy) mergeStrategy).merge(parallelResults, state);
        } else {
            merged = new HashMap<>(parallelResults);
        }

        if (outputKey != null && !outputKey.trim().isEmpty()) {
            resultMap.put(outputKey, merged);
            logger.info("Enhanced result aggregation completed. Final result stored under key: {}",
                    outputKey);
        } else {
            logger.info("Enhanced result aggregation completed. No outputKey specified, " +
                    "skipping merged result storage.");
        }

        Map<String, KeyStrategy> strategies = new HashMap<>();
        for (String key : parallelResults.keySet()) {
            strategies.put(key, KeyStrategy.REPLACE);
        }

        state.updateStateWithKeyStrategies(parallelResults, strategies);
        resultMap.putAll(parallelResults);

        return resultMap;
    }
}
