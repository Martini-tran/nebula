package com.nebula.common.ai.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回调重发驱动（W2 自动重发）
 * 扫出到点可重发的投递（{@code status=FAILED AND next_retry_at<=now AND attempts<max}）逐个 redeliver，
 * 补齐 W1 "无自动重发"的空窗（见 docs/编排回调Webhook设计.md 5.4）。与 {@code IterationDriver} 同一分层：
 * 纯业务 {@link #retryDue(LocalDateTime)}，定时触发由业务服务的 {@code @Scheduled} 持有。
 *
 * <p><b>无需分布式锁</b>：重发不像链推进那样有"seq 前进"的副作用；多节点并发重发同一条，最坏让消费端多收一次
 * 幂等请求（消费端按 deliveryId / chainId+seq 去重），可接受。redeliver 内部失败只落 FAILED、不抛。
 *
 * @author nebula
 */
public class WebhookRetryDriver {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryDriver.class);

    /**
     * 单次扫描最多重发多少条，防一次扫太多拖垮线程
     */
    private static final int DEFAULT_BATCH = 100;

    private final WebhookDeliveryStore store;

    private final WebhookDispatcher dispatcher;

    public WebhookRetryDriver(WebhookDeliveryStore store, WebhookDispatcher dispatcher) {
        this.store = store;
        this.dispatcher = dispatcher == null ? WebhookDispatcher.NOOP : dispatcher;
    }

    /**
     * 扫出到点可重发的投递并逐个重发。由业务服务的 {@code @Scheduled} 定时调用。
     *
     * @param now 当前时间
     * @return 本次尝试重发的条数
     */
    public int retryDue(LocalDateTime now) {
        if (store == null) {
            return 0;
        }
        List<WebhookDelivery> due = store.findRetriable(now, DEFAULT_BATCH);
        int count = 0;
        for (WebhookDelivery d : due) {
            try {
                dispatcher.redeliver(d.deliveryId());
                count++;
            } catch (RuntimeException e) {
                // redeliver 承诺不抛，这里再兜一层：单条异常不影响同批其它重发
                log.warn("重发投递[{}]异常（跳过，下轮继续）: {}", d.deliveryId(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("本轮自动重发 {} 条失败投递", count);
        }
        return count;
    }
}
