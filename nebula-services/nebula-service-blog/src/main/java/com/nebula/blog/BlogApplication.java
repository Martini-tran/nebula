package com.nebula.blog;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 博客服务启动类
 *
 * <p>配置名固定为 {@code nebula-blog}（而非默认 {@code application}）：既支持独立部署，也支持被
 * {@code nebula-service-all} 聚合到同一 JVM——多个服务同处一个 classpath 时，同名 {@code application.yml}
 * 会互相覆盖，改用唯一配置名 {@code nebula-blog.yml} 后各服务上下文各读各的。</p>
 */
@SpringBootApplication
public class BlogApplication {

    /** 配置名，供 {@code nebula-service-all} 聚合启动时复用，避免魔法字符串散落。 */
    public static final String CONFIG_NAME = "nebula-blog";

    public static void main(String[] args) {
        new SpringApplicationBuilder(BlogApplication.class)
                .properties("spring.config.name=" + CONFIG_NAME)
                .run(args);
    }

}
