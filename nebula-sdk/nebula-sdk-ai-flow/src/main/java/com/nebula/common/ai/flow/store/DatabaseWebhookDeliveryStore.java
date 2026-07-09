package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.ai.webhook.WebhookDelivery;
import com.nebula.common.ai.webhook.WebhookDeliveryStore;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库版回调投递记录存储
 * 以 MyBatis-Plus 落 {@code ai_webhook_delivery}，实现 SDK 的 {@link WebhookDeliveryStore}。
 * 与 {@link DatabaseIterationChainStore} 同构，由 {@link AiFlowStoreAutoConfiguration} 装配。
 *
 * @author nebula
 */
@Slf4j
public class DatabaseWebhookDeliveryStore implements WebhookDeliveryStore {

    private final AiWebhookDeliveryMapper mapper;

    public DatabaseWebhookDeliveryStore(AiWebhookDeliveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean create(WebhookDelivery d) {
        // 幂等：deliveryId 已存在视为重复投递，跳过
        Long exists = mapper.selectCount(new LambdaQueryWrapper<AiWebhookDelivery>()
                .eq(AiWebhookDelivery::getDeliveryId, d.deliveryId()));
        if (exists != null && exists > 0) {
            log.debug("投递[{}]已存在，跳过重复落库", d.deliveryId());
            return false;
        }
        AiWebhookDelivery row = new AiWebhookDelivery();
        row.setDeliveryId(d.deliveryId());
        row.setInstanceId(d.instanceId());
        row.setChainId(d.chainId());
        row.setSeq(d.seq());
        row.setAgentCode(d.agentCode());
        row.setNodeCode(d.nodeCode());
        row.setEvent(d.event());
        row.setUrl(d.url());
        row.setMode(d.mode() == null ? WebhookDelivery.MODE_INLINE : d.mode());
        row.setPayload(FlowJsonCodec.write(d.payload()));
        row.setStatus("PENDING");
        row.setAttempts(0);
        row.setMaxAttempts(8);
        try {
            mapper.insert(row);
            return true;
        } catch (RuntimeException e) {
            // 并发下 uk_delivery_id 撞键：视为已存在，幂等跳过
            log.debug("投递[{}]落库撞唯一键（并发重复），跳过: {}", d.deliveryId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void markSuccess(String deliveryId, int responseCode) {
        mapper.update(null, new LambdaUpdateWrapper<AiWebhookDelivery>()
                .eq(AiWebhookDelivery::getDeliveryId, deliveryId)
                .set(AiWebhookDelivery::getStatus, "SUCCESS")
                .set(AiWebhookDelivery::getResponseCode, responseCode)
                .set(AiWebhookDelivery::getLastError, null)
                .set(AiWebhookDelivery::getNextRetryAt, null)
                .setSql("attempts = attempts + 1"));
    }

    @Override
    public void markFailed(String deliveryId, Integer responseCode, String error, LocalDateTime nextRetryAt) {
        AiWebhookDelivery row = findByDeliveryId(deliveryId);
        if (row == null) {
            log.warn("标记投递失败找不到记录[{}]", deliveryId);
            return;
        }
        int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
        int max = row.getMaxAttempts() == null ? 8 : row.getMaxAttempts();
        // 达上限 → DEAD（停发，留手动/告警）；否则 FAILED + next_retry_at（可重发）
        String status = attempts >= max ? "DEAD" : "FAILED";
        // 退避：调用方未指定则按 attempts 指数退避（1,2,4,…,封顶 60 分钟），退避策略归 store（它有 attempts）
        LocalDateTime retryAt = nextRetryAt;
        if (retryAt == null && "FAILED".equals(status)) {
            long minutes = Math.min(1L << Math.min(attempts, 6), 60L);
            retryAt = now().plusMinutes(minutes);
        }
        LambdaUpdateWrapper<AiWebhookDelivery> upd = new LambdaUpdateWrapper<AiWebhookDelivery>()
                .eq(AiWebhookDelivery::getDeliveryId, deliveryId)
                .set(AiWebhookDelivery::getStatus, status)
                .set(AiWebhookDelivery::getAttempts, attempts)
                .set(AiWebhookDelivery::getResponseCode, responseCode)
                .set(AiWebhookDelivery::getLastError, truncate(error))
                .set(AiWebhookDelivery::getNextRetryAt, "DEAD".equals(status) ? null : retryAt);
        mapper.update(null, upd);
        if ("DEAD".equals(status)) {
            log.warn("投递[{}]重发达上限 {} 次，置 DEAD 停发", deliveryId, max);
        }
    }

    @Override
    public WebhookDelivery load(String deliveryId) {
        AiWebhookDelivery row = findByDeliveryId(deliveryId);
        return row == null ? null : toDelivery(row);
    }

    @Override
    public List<WebhookDelivery> findRetriable(LocalDateTime now, int limit) {
        List<AiWebhookDelivery> rows = mapper.selectList(new LambdaQueryWrapper<AiWebhookDelivery>()
                .eq(AiWebhookDelivery::getStatus, "FAILED")
                .le(AiWebhookDelivery::getNextRetryAt, now)
                .orderByAsc(AiWebhookDelivery::getNextRetryAt)
                .last("limit " + Math.max(1, limit)));
        return rows.stream().map(this::toDelivery).toList();
    }

    /* ===================== 内部 ===================== */

    private WebhookDelivery toDelivery(AiWebhookDelivery r) {
        return new WebhookDelivery(
                r.getDeliveryId(), r.getInstanceId(), r.getChainId(), r.getSeq(),
                r.getAgentCode(), r.getNodeCode(), r.getEvent(), r.getUrl(),
                r.getMode(), FlowJsonCodec.readObjectMap(r.getPayload()));
    }

    private AiWebhookDelivery findByDeliveryId(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<AiWebhookDelivery>()
                .eq(AiWebhookDelivery::getDeliveryId, deliveryId)
                .last("limit 1"));
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
