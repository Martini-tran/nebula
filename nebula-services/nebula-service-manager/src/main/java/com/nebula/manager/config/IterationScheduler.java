package com.nebula.manager.config;

import com.nebula.common.ai.iteration.IterationDriver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 迭代链定时触发器
 * 每分钟调一次 {@link IterationDriver#tick}，扫出到点的链各推进一轮。定时器由本类（业务服务）持有，
 * 而非 SDK——SDK 的 {@code IterationDriver} 只提供纯业务的 tick，不自作主张开 {@code @EnableScheduling}。
 *
 * <p>多实例部署下每个节点都会定时触发，靠 {@link RedisIterationLock} 抢占保证同一轮只有一个节点推进
 * （见 docs/跨实例迭代层设计.md 5.1）。需在启动类开启 {@code @EnableScheduling}。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class IterationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IterationScheduler.class);

    private final IterationDriver iterationDriver;

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
}
