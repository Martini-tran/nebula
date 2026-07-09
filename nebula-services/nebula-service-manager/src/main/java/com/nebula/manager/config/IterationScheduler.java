package com.nebula.manager.config;

import com.nebula.common.ai.iteration.IterationDriver;
import com.nebula.common.ai.webhook.WebhookRetryDriver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 迭代链 / 回调定时触发器
 * 承载两个定时任务：① {@link IterationDriver#tick} 扫到点的链各推进一轮；② {@link WebhookRetryDriver#retryDue}
 * 扫失败回调自动重发（W2）。定时器由本类（业务服务）持有，而非 SDK——SDK 的 driver 只提供纯业务方法，
 * 不自作主张开 {@code @EnableScheduling}。
 *
 * <p>多实例部署下每个节点都会定时触发，链推进靠 {@link RedisIterationLock} 抢占防重跑；回调重发幂等（消费端按
 * deliveryId 去重），无需锁。需在启动类开启 {@code @EnableScheduling}。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class IterationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IterationScheduler.class);

    private final IterationDriver iterationDriver;

    private final WebhookRetryDriver webhookRetryDriver;

    /**
     * 每分钟扫描到点的链并推进。固定频率（上次开始后 60s），到点判定靠 {@code next_run_at <= now}，
     * 分钟级扫描足以支撑天级/小时级的系列节律。
     */
    @Scheduled(fixedDelayString = "${nebula.ai.iteration.scan-interval-ms:60000}")
    public void scan() {
        try {
            int advanced = iterationDriver.tick(LocalDateTime.now());
            if (advanced > 0) {
                log.info("迭代链本轮扫描推进 {} 条", advanced);
            }
        } catch (RuntimeException e) {
            // 调度线程异常必须吞掉，否则 fixedDelay 任务会被 Spring 静默取消，之后再不触发
            log.error("迭代链扫描异常（本轮跳过，下轮继续）: {}", e.getMessage(), e);
        }
    }

    /**
     * 每分钟扫描失败的回调投递并自动重发（W2）。到点判定靠 {@code next_retry_at <= now}（指数退避）。
     */
    @Scheduled(fixedDelayString = "${nebula.ai.webhook.retry-interval-ms:10000}")
    public void retryWebhooks() {
        try {
            webhookRetryDriver.retryDue(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.error("回调自动重发扫描异常（本轮跳过，下轮继续）: {}", e.getMessage(), e);
        }
    }
}
