package com.nebula.manager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统管理模块应用启动类
 * 配置Spring Boot应用程序的基本设置和MyBatis映射器扫描路径。
 * {@code @EnableScheduling} 启用定时任务，驱动 {@code IterationScheduler} 扫描跨实例迭代链（系列递推）。
 *
 * <p>配置名固定为 {@code nebula-manager}（而非默认 {@code application}）：既支持独立部署，也支持被
 * {@code nebula-service-all} 聚合到同一 JVM——多个服务同处一个 classpath 时同名 {@code application.yml}
 * 会互相覆盖，改用唯一配置名 {@code nebula-manager.yml} 后各服务上下文各读各的。</p>
 *
 * @author nebula
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.nebula.manager.mapper")
public class ManagerApplication {

    /** 配置名，供 {@code nebula-service-all} 聚合启动时复用。 */
    public static final String CONFIG_NAME = "nebula-manager";

    /**
     * 应用程序主入口方法
     * 启动Spring Boot应用程序
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(ManagerApplication.class)
                .properties("spring.config.name=" + CONFIG_NAME)
                .run(args);
    }
}
