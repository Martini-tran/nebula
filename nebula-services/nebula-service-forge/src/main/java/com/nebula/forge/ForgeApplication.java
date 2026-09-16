package com.nebula.forge;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 锻造服务启动类
 *
 * <p>配置名固定为 {@code nebula-forge}，理由同 {@code BlogApplication}：支持被 {@code nebula-service-all}
 * 聚合到同一 JVM 时各服务配置互不覆盖。</p>
 */
@SpringBootApplication
public class ForgeApplication {

    /** 配置名，供 {@code nebula-service-all} 聚合启动时复用。 */
    public static final String CONFIG_NAME = "nebula-forge";

    public static void main(String[] args) {
        new SpringApplicationBuilder(ForgeApplication.class)
                .properties("spring.config.name=" + CONFIG_NAME)
                .run(args);
    }

}
