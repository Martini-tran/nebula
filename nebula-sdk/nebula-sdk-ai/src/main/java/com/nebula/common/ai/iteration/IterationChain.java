package com.nebula.common.ai.iteration;

import java.util.Map;

/**
 * 迭代链快照（读侧值对象）
 * 对应 {@code ai_agent_iteration} 一行，是"跨实例递推"的长期状态载体（= 一个"系列"实例）。
 * {@link IterationDriver} 据此组装每一轮实例的入参、调 {@code AgentEngine} 推进、再回写链。
 *
 * <p>与 {@code AgentInstanceSnapshot}（单次执行的自包含运行态）正交：链是"执行与执行之间"的时间线，
 * 实例是链上被反复创建、跑完即弃的一个个节点。链靠 {@link #lastInstanceId} 指针回溯上一轮产物，
 * 无需扫描（见 docs/跨实例迭代层设计.md）。
 *
 * @param chainId        迭代链唯一标识（业务键）
 * @param name           链名称（如"30天Java进阶"）
 * @param agentCode      每一轮用哪个 Agent 跑
 * @param cron           推进节律（如 "0 0 9 * * ?"）
 * @param seq            已完成轮次（= 已写到第几篇）
 * @param maxIterations  轮次上限（null=不限）
 * @param untilExpr      出链条件 SpEL（对上一轮产物求值；null/空=不判，只靠 maxIterations 收尾）
 * @param carryOver      carry-over 映射：上一轮产物键 → 下一轮 inputs 键
 * @param seedInputs     首轮种子入参（第 0 轮无上一轮时的启动数据）
 * @param lastInstanceId 上一轮实例 id（carry-over 数据源指针；首轮为 null）
 * @param userId         归属用户ID
 * @param conversationId 关联会话ID
 * @param status         ACTIVE | PAUSED | COMPLETED | FAILED
 * @param lockVersion    乐观锁版本（advance 用 CAS 防同一轮重复推进）
 *
 * @author nebula
 */
public record IterationChain(
        String chainId,
        String name,
        String agentCode,
        String cron,
        int seq,
        Integer maxIterations,
        String untilExpr,
        Map<String, String> carryOver,
        Map<String, Object> seedInputs,
        String lastInstanceId,
        String userId,
        String conversationId,
        String status,
        int lockVersion,
        String webhookUrl) {

    /**
     * 是否首轮（尚无上一轮实例，用 seedInputs 启动）
     *
     * @return lastInstanceId 为空时 true
     */
    public boolean isFirstRound() {
        return lastInstanceId == null || lastInstanceId.isBlank();
    }

    /**
     * 是否配了回调（每轮 advance 成功后 POST 产物到 webhookUrl）
     *
     * @return webhookUrl 非空时 true
     */
    public boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
