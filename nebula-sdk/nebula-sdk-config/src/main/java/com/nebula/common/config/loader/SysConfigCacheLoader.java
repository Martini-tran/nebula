package com.nebula.common.config.loader;

import com.nebula.common.config.service.impl.SysConfigServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 启动时全量加载 sys_config 到 Redis。
 * 启动失败不阻断应用启动（DB / Redis 临时不可达时打 warn，后续可手动 refresh）。
 *
 * @author nebula
 */
@Slf4j
public class SysConfigCacheLoader implements ApplicationRunner {

    private final SysConfigServiceImpl service;

    public SysConfigCacheLoader(SysConfigServiceImpl service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = service.applyAllFromDb();
            log.info("[nebula-config] sys_config loaded into Redis, count={}", count);
        } catch (Exception e) {
            log.warn("[nebula-config] sys_config load failed: {}", e.getMessage());
        }
    }
}
