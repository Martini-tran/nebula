package com.nebula.common.ai.webhook;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回调投递记录存储（SPI）
 * 把每次回调落 {@code ai_webhook_delivery}，支撑日志与断点续发（见 docs/编排回调Webhook设计.md 第五章）。
 * SDK 仅定义接口；数据库实现由 {@code nebula-sdk-ai-flow} 提供，与 {@code IterationChainStore} 同一分层。
 *
 * @author nebula
 */
public interface WebhookDeliveryStore {

    /**
     * 落一条投递记录（PENDING）。若 {@code deliveryId} 已存在则视为重复投递，直接返回 false（幂等）。
     *
     * @param delivery 投递内容
     * @return 新建成功 true；已存在（重复）false
     */
    boolean create(WebhookDelivery delivery);

    /**
     * 标记投递成功（HTTP 2xx）。
     *
     * @param deliveryId   投递标识
     * @param responseCode HTTP 响应码
     */
    void markSuccess(String deliveryId, int responseCode);

    /**
     * 标记投递失败：attempts+1，记错误；未达上限置 {@code FAILED} + {@code next_retry_at}，达上限置 {@code DEAD}。
     *
     * @param deliveryId   投递标识
     * @param responseCode HTTP 响应码（无响应传 null）
     * @param error        失败原因
     * @param nextRetryAt  下次重发时间（未达上限时）
     */
    void markFailed(String deliveryId, Integer responseCode, String error, LocalDateTime nextRetryAt);

    /**
     * 读取一条投递记录（重发/查看用）。
     *
     * @param deliveryId 投递标识
     * @return 记录；不存在返回 null
     */
    WebhookDelivery load(String deliveryId);

    /**
     * 扫出到点可重发的记录：{@code status='FAILED' AND next_retry_at<=now AND attempts<max_attempts}（W2 重发扫描器用）。
     *
     * @param now   当前时间
     * @param limit 单次最多取多少条
     * @return 可重发记录列表
     */
    List<WebhookDelivery> findRetriable(LocalDateTime now, int limit);
}
