package com.nebula.manager.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能体实例详情（管理员端，含转移历史时间线，用于回放）
 *
 * @author nebula
 */
@Data
public class AgentInstanceVO {

    /**
     * 实例标识
     */
    private String instanceId;

    /**
     * 归属 Agent 编码
     */
    private String agentCode;

    /**
     * 引用的编排图编码
     */
    private String flowCode;

    /**
     * 实例状态：RUNNING | SUSPENDED | SUCCESS | FAILED
     */
    private String status;

    /**
     * 当前状态（节点编码）
     */
    private String currentState;

    /**
     * SUSPENDED 时等待的事件名集合
     */
    private List<String> awaitingEvents = new ArrayList<>();

    /**
     * 已转移次数
     */
    private Integer transitionCount;

    /**
     * 上下文快照产物（分级落盘，可能非最新——回放以转移时间线为准）
     */
    private Map<String, Object> contextSnapshot = new LinkedHashMap<>();

    /**
     * 转移历史时间线（按 seq 升序，含 RETRY 行）
     */
    private List<AgentTransitionVO> transitions = new ArrayList<>();

    /**
     * 一条转移记录（回放/审计）
     */
    @Data
    public static class AgentTransitionVO {

        /**
         * 转移序号（重试也占号）
         */
        private Integer seq;

        /**
         * 源状态（可空）
         */
        private String fromState;

        /**
         * 目标状态
         */
        private String toState;

        /**
         * 触发事件名（signal 唤醒用，可空）
         */
        private String eventName;

        /**
         * 尝试次数（0=首次）
         */
        private Integer attempt;

        /**
         * 结果：SUCCESS | RETRY | FAILED | COMPENSATED
         */
        private String outcome;

        /**
         * 该步对 context 的变更集（SUCCESS 行为产物 delta；RETRY/FAILED 行为空）
         */
        private Map<String, Object> nodeResult = new LinkedHashMap<>();
    }
}
