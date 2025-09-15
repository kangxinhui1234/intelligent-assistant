package com.kxh.aiagent.model;

/**
 * 代理执行状态的枚举类   代表了一个智能体任务实例
 */
public enum AgentState {

    /**
     * 空闲状态
     */
    IDLE,

    /**
     * 运行中状态
     */
    RUNNING,

    /**
     * 已完成状态
     */
    FINISHED,

    /**
     * 错误状态
     */
    ERROR
}
