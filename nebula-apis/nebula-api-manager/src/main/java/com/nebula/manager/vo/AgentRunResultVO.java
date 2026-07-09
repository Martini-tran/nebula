package com.nebula.manager.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能体运行/唤醒/续跑结果（管理员端）
 * 承载一次执行后的实例状态与编排产物：run/signal/resume 三个入口共用。
 *
 * @author nebula
 */
@Data
public class AgentRunResultVO {

    /**
     * 实例标识（续跑/唤醒/回放入口）
     */
    private String instanceId;

    /**
     * 归属 Agent 编码
     */
    private String agentCode;

    /**
     * 实例状态：RUNNING | SUSPENDED | SUCCESS | FAILED
     */
    private String status;

    /**
     * 当前状态（节点编码）
     */
    private String currentState;

    /**
     * SUSPENDED 时等待的事件名集合（signal 命中其一即唤醒）
     */
    private List<String> awaitingEvents = new ArrayList<>();

    /**
     * 编排上下文全部产物
     */
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
