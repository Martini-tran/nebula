package com.nebula.common.ai.webhook;

import java.util.Map;

/**
 * 一次编排回调投递（值对象）
 * 对应 {@code ai_webhook_delivery} 一行，既是日志又是重发依据（见 docs/编排回调Webhook设计.md）。
 * {@code payload} 是要 POST 的回调 body（重发时直接读它，不重跑 Agent）；鉴权 headers <b>不</b>进本对象/表，
 * 重发时从配置源实时取。
 *
 * @param deliveryId   投递唯一标识（业务键，重发幂等键之一）
 * @param instanceId   来源实例（可空）
 * @param chainId      来源迭代链（若来自链）
 * @param seq          迭代链第几轮（幂等：chainId+seq 唯一定位一篇产物）
 * @param agentCode    Agent 编码
 * @param nodeCode     节点级回调才有
 * @param event        事件：ITERATION_ADVANCED | INSTANCE_SUCCESS | INSTANCE_FAILED | NODE_SUCCESS | NODE_FAILED
 * @param url          回调地址
 * @param mode         发送时机：INLINE(当场发) | DEFER(线程池发)——仅时机，与可靠性无关
 * @param payload      回调 body（JSON 可序列化的 Map）
 *
 * @author nebula
 */
public record WebhookDelivery(
        String deliveryId,
        String instanceId,
        String chainId,
        Integer seq,
        String agentCode,
        String nodeCode,
        String event,
        String url,
        String mode,
        Map<String, Object> payload) {

    /** 发送时机：当场发 */
    public static final String MODE_INLINE = "INLINE";

    /** 发送时机：线程池发（W2） */
    public static final String MODE_DEFER = "DEFER";

    /** 事件：迭代链某轮 advance 成功 */
    public static final String EVENT_ITERATION_ADVANCED = "ITERATION_ADVANCED";
}
