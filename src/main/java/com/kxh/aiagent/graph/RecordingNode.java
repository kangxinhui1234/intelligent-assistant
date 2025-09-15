package com.kxh.aiagent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
@Slf4j
public class RecordingNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState t) throws Exception {
        log.info("record:"+t.value("aaa"));
        return null;
    }
}
