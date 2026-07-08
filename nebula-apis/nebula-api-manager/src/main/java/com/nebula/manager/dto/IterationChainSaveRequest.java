package com.nebula.manager.dto;

import lombok.Data;

import java.util.Map;

/**
 * 跨实例迭代链创建/更新请求
 * 维护 {@code ai_agent_iteration}：每轮用哪个 Agent 跑、多久推进一轮、上一轮产物如何映射进下一轮入参、何时出链。
 * 对应 docs/跨实例迭代层设计.md。
 *
 * @author nebula
 */
@Data
public class IterationChainSaveRequest {

    /**
     * 链名称，如"30天Java进阶"（可空）
     */
    private String name;

    /**
     * 每一轮用哪个 Agent 跑（必填）
     */
    private String agentCode;

    /**
     * 推进节律 cron，如 "0 0 9 * * ?"（每天9点）（必填）
     */
    private String cron;

    /**
     * 轮次上限（可空，防无限连载；到顶置 COMPLETED）
     */
    private Integer maxIterations;

    /**
     * 出链条件 SpEL（对上一轮产物求值），如 "getString('outlineDone') == 'true'"（可空）
     */
    private String untilExpr;

    /**
     * carry-over 映射：上一轮产物键 → 下一轮 inputs 键（可空则每轮独立跑）
     */
    private Map<String, String> carryOver;

    /**
     * 首轮种子入参：第 0 轮没有上一轮，用它启动（如系列主题）
     */
    private Map<String, Object> seedInputs;

    /**
     * 归属用户ID（可空）
     */
    private String userId;

    /**
     * 关联会话ID（可空）
     */
    private String conversationId;

    /**
     * 首轮触发时间（可空，缺省立即到点：创建后下次扫描即跑首轮）
     */
    private java.time.LocalDateTime firstRunAt;
}
