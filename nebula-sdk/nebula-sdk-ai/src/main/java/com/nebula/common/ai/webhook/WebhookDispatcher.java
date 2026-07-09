package com.nebula.common.ai.webhook;

/**
 * 回调投递器（SPI）
 * "给我一个 delivery，我负责发 + 记录"——收口"落投递记录 → 发 HTTP → 回写结果"（见 docs/编排回调Webhook设计.md 第六章）。
 * SDK 仅定义接口，HTTP 实现由 {@code nebula-sdk-ai-flow} 提供（不把 HTTP 客户端引入 SDK）。
 *
 * <p><b>非阻塞语义</b>：dispatch 失败只落 {@code FAILED} 记录，<b>绝不抛出、绝不阻塞主流程</b>——调用方（如
 * {@code IterationDriver} 在 advance 成功后）不因回调失败而回滚链推进（可靠性靠 delivery 表 + 重发，非同步）。
 *
 * @author nebula
 */
public interface WebhookDispatcher {

    /**
     * 投递一次回调：落 PENDING 记录 → 按 mode 发送（W1 只 INLINE 当场发）→ 回写 SUCCESS/FAILED。
     * <b>幂等</b>：相同 {@code deliveryId} 已存在则跳过（不重复落库、不重复发）。<b>不抛异常。</b>
     *
     * @param delivery 投递内容（含 payload 与 url）
     */
    void dispatch(WebhookDelivery delivery);

    /**
     * 重发一条已存在的投递（手动重发 / W2 重发扫描器）：读回 payload 重发，不重跑 Agent。<b>不抛异常。</b>
     *
     * @param deliveryId 投递标识
     * @return 重发后是否成功（记录不存在返回 false）
     */
    boolean redeliver(String deliveryId);

    /**
     * 缺省空实现：未配置回调基建时（无 store/无 HTTP）dispatch/redeliver 均为 no-op，回调静默跳过。
     */
    WebhookDispatcher NOOP = new WebhookDispatcher() {
        @Override
        public void dispatch(WebhookDelivery delivery) {
            // no-op
        }

        @Override
        public boolean redeliver(String deliveryId) {
            return false;
        }
    };
}
