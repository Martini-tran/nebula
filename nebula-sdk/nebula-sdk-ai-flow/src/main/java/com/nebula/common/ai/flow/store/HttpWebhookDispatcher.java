package com.nebula.common.ai.flow.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.webhook.WebhookDelivery;
import com.nebula.common.ai.webhook.WebhookDeliveryStore;
import com.nebula.common.ai.webhook.WebhookDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * HTTP 版回调投递器
 * 用 httpclient5 把回调 body POST 到目标 URL，实现 SDK 的 {@link WebhookDispatcher}（复用与 {@code OpenAiChatProvider}
 * 一致的 HTTP 客户端）。收口"落投递记录 → 发 → 回写"，<b>失败只落 FAILED 记录、绝不抛异常、绝不阻塞主流程</b>
 * （见 docs/编排回调Webhook设计.md 第六章）。
 *
 * @author nebula
 */
@Slf4j
public class HttpWebhookDispatcher implements WebhookDispatcher {

    private final WebhookDeliveryStore store;

    private final CloseableHttpClient httpClient;

    private final ObjectMapper objectMapper;

    public HttpWebhookDispatcher(WebhookDeliveryStore store, CloseableHttpClient httpClient,
                                 ObjectMapper objectMapper) {
        this.store = store;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public void dispatch(WebhookDelivery delivery) {
        if (delivery == null || delivery.url() == null || delivery.url().isBlank()) {
            return;
        }
        // 幂等落 PENDING：已存在（重复投递）则跳过，不重复发
        boolean created = store.create(delivery);
        if (!created) {
            log.debug("投递[{}]已存在，跳过 dispatch", delivery.deliveryId());
            return;
        }
        send(delivery);
    }

    @Override
    public boolean redeliver(String deliveryId) {
        WebhookDelivery delivery = store.load(deliveryId);
        if (delivery == null) {
            log.warn("重发找不到投递记录[{}]", deliveryId);
            return false;
        }
        return send(delivery);
    }

    /**
     * 实发一次：POST payload → 2xx 记 SUCCESS，否则记 FAILED（退避由 store 按 attempts 自算）。不抛异常。
     */
    private boolean send(WebhookDelivery delivery) {
        String body;
        try {
            body = objectMapper.writeValueAsString(delivery.payload());
        } catch (Exception e) {
            store.markFailed(delivery.deliveryId(), null, "payload 序列化失败: " + e.getMessage(), null);
            log.warn("投递[{}]payload 序列化失败: {}", delivery.deliveryId(), e.getMessage());
            return false;
        }
        try {
            HttpPost post = new HttpPost(delivery.url());
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            return httpClient.execute(post, resp -> {
                int code = resp.getCode();
                if (code >= 200 && code < 300) {
                    store.markSuccess(delivery.deliveryId(), code);
                    log.info("投递[{}]成功 → {} ({})", delivery.deliveryId(), delivery.url(), code);
                    return true;
                }
                store.markFailed(delivery.deliveryId(), code, "HTTP " + code, null);
                log.warn("投递[{}]失败 HTTP {} → {}", delivery.deliveryId(), code, delivery.url());
                return false;
            });
        } catch (Exception e) {
            // 网络异常等：落 FAILED，交重发（不抛，不阻塞主流程）
            store.markFailed(delivery.deliveryId(), null, e.getMessage(), null);
            log.warn("投递[{}]异常 → {}: {}", delivery.deliveryId(), delivery.url(), e.getMessage() , e);
            return false;
        }
    }
}
