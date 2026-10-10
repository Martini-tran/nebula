package com.nebula.manager.config;

import com.nebula.manager.service.SysApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * API Key 缓存同步
 * 网关没有数据库，校验 key 只读 Sa-Token 写在 Redis 里的那份。Redis 重启或淘汰掉这些键之后，
 * 库里明明有的 key 会被判成无效，且无人值守的调用方不会像登录用户那样自己重新登录——
 * 所以启动时灌一次，之后定期重灌，把故障窗口限制在一个周期内。
 *
 * @author nebula
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyCacheSyncer {

    private final SysApiKeyService apiKeyService;

    /**
     * 启动后立即执行一次，之后按固定间隔重灌（默认 10 分钟）。
     */
    @Scheduled(fixedDelayString = "${nebula.security.api-key.resync-interval-ms:600000}")
    public void sync() {
        try {
            int synced = apiKeyService.syncToCache();
            log.debug("API Key 缓存同步完成，共 {} 条", synced);
        } catch (Exception e) {
            // 库或 Redis 暂时不可用时不让定时线程带着异常退出，下个周期再试
            log.warn("API Key 缓存同步失败: {}", e.getMessage());
        }
    }
}
