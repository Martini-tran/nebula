package com.nebula.all;

import com.nebula.blog.BlogApplication;
import com.nebula.forge.ForgeApplication;
import com.nebula.manager.ManagerApplication;
import com.nebula.space.SpaceApplication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 单进程聚合启动器。
 *
 * <p><b>做什么：</b>在同一个 JVM 内顺序拉起 manager / blog / space / forge 四个服务。每个服务用它自己的启动类
 * 与配置名建一个<b>相互独立</b>的 Spring 上下文，并各自启动一个绑定到自己端口、context-path 的内嵌 Tomcat。
 * {@code run} 启动完 Web 服务器后即返回（服务器在后台线程运行），因此顺序调用即可让多个服务并存于一个进程。</p>
 *
 * <p><b>为什么用「多上下文」而不是「单上下文大杂烩」：</b>四个服务各有一个 {@code @Component StpInterfaceImpl}
 * （Sa-Token 只允许一个 {@code StpInterface} Bean）、控制器都不带服务前缀而靠 context-path 区分（合并后
 * {@code /admin/*}、{@code /front/*} 会撞车）、以及大量同名配置 Bean。放进各自隔离的上下文后，这些冲突全部消失，
 * 每个服务的行为与独立部署完全一致——这正是本方案「可信」的核心：零业务代码改动、可随时回退到分开部署。</p>
 *
 * <p><b>Bean 隔离但 classpath 共享：</b>上下文互不可见，但类路径是全进程共享的，因此某个服务独有的依赖
 * （如只有 manager 依赖的 sdk-ai-flow）所带的自动配置，也会被其它服务的上下文看见。这类自动配置若硬依赖
 * 本服务没有的 bean，就会让该上下文启动失败——修法是让自动配置带上与上游一致的条件（参见
 * {@code AiFlowStoreAutoConfiguration} 上的 {@code @ConditionalOnBean(AiService.class)}），而不是改业务代码。</p>
 *
 * <p><b>为什么不含 gateway：</b>gateway 基于 WebFlux（{@code spring-cloud-starter-gateway-server-webflux}），
 * 与 servlet 服务的 {@code spring-webmvc} 同处一个 classpath 时 Spring Cloud Gateway 会直接拒绝启动。gateway
 * 继续独立运行，只需把它的路由目标主机指向本聚合容器即可（端口不变）。</p>
 *
 * <p><b>配置隔离：</b>合并后所有服务同处一个 classpath，若都叫 {@code application.yml} 会互相覆盖。各服务已把
 * 配置重命名为 {@code nebula-<svc>.yml}，此处按 {@code XxxApplication.CONFIG_NAME} 指定 {@code spring.config.name}，
 * 与各服务独立启动时的行为一致。</p>
 *
 * <p><b>可选择子集：</b>通过环境变量 {@code NEBULA_SERVICES}（或系统属性 {@code nebula.services}）指定要启动的
 * 服务，逗号分隔，缺省全部。例如只跑管理端与博客：{@code NEBULA_SERVICES=manager,blog}。</p>
 */
public final class NebulaAllInOneApplication {

    /**
     * 一个待聚合的服务。
     *
     * @param name             服务名（{@code NEBULA_SERVICES} 里使用的标识）
     * @param applicationClass 该服务的 Spring Boot 启动类
     * @param configName       该服务的 {@code spring.config.name}
     */
    private record Service(String name, Class<?> applicationClass, String configName) {
    }

    /** 服务名 -> 服务。用 LinkedHashMap 固定启动顺序：manager 最重、依赖最全，先起。 */
    private static final Map<String, Service> SERVICES = new LinkedHashMap<>();

    static {
        register(new Service("manager", ManagerApplication.class, ManagerApplication.CONFIG_NAME));
        register(new Service("blog", BlogApplication.class, BlogApplication.CONFIG_NAME));
        register(new Service("space", SpaceApplication.class, SpaceApplication.CONFIG_NAME));
        register(new Service("forge", ForgeApplication.class, ForgeApplication.CONFIG_NAME));
    }

    private static void register(Service service) {
        SERVICES.put(service.name(), service);
    }

    private NebulaAllInOneApplication() {
    }

    public static void main(String[] args) {
        List<String> enabled = resolveEnabledServices();

        System.out.printf("[nebula-all] 本进程将启动以下服务: %s%n", String.join(", ", enabled));

        // 持有已起的上下文：任一服务失败时要把它们逐个关掉，否则内嵌 Tomcat 的非守护线程会让
        // JVM 继续存活——进程「半死不活」地只提供一部分服务，是聚合部署下最危险的状态。
        List<ConfigurableApplicationContext> started = new ArrayList<>();

        for (String name : enabled) {
            Service service = SERVICES.get(name);
            long start = System.currentTimeMillis();
            System.out.printf("[nebula-all] 正在启动 %s ...%n", name);
            try {
                started.add(new SpringApplicationBuilder(service.applicationClass())
                        .properties(jvmScopedProperties(service))
                        .run(args));
            } catch (Throwable ex) {
                // fail-fast：聚合部署下「部分服务起不来」需要人工介入，带病运行只会让健康检查
                // （只探 manager）误报健康。先关已起的上下文释放端口，再以非零码退出，
                // 交给编排层按 restart 策略拉起。
                System.err.printf("[nebula-all] 服务 %s 启动失败，正在关闭已启动的 %d 个服务并退出%n",
                        name, started.size());
                ex.printStackTrace();
                shutdown(started);
                System.exit(1);
            }
            System.out.printf("[nebula-all] %s 启动完成，耗时 %d ms%n", name, System.currentTimeMillis() - start);
        }

        System.out.printf("[nebula-all] 全部服务启动完成，共 %d 个%n", enabled.size());
    }

    /**
     * 单 JVM 多上下文下必须「按服务隔离」的属性。
     *
     * <p>{@code spring.config.name} 让各上下文读各自的 {@code nebula-<svc>.yml}。其余三项都是为了避免 JMX
     * MBean 名冲突——多个 {@code SpringApplication} 在同一 JVM 里会各自向平台 MBeanServer 注册同名 MBean，
     * 第二个上下文注册时抛 {@code InstanceAlreadyExistsException}：
     * <ul>
     *   <li>{@code spring.application.admin.jmx-name}：Spring Boot Admin MBean 名固定为
     *       {@code org.springframework.boot:type=Admin,name=SpringApplication}（{@code spring.application.admin.enabled=true}
     *       时装配），按服务加后缀区分。</li>
     *   <li>{@code spring.jmx.default-domain} / {@code management.endpoints.jmx.domain}：让各上下文的 JMX
     *       MBean（含 actuator 端点）落在独立 domain，互不撞名。</li>
     * </ul>
     */
    private static Map<String, Object> jvmScopedProperties(Service service) {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.config.name", service.configName());
        props.put("spring.jmx.default-domain", "nebula-" + service.name());
        props.put("management.endpoints.jmx.domain", "nebula-" + service.name());
        props.put("spring.application.admin.jmx-name",
                "org.springframework.boot:type=Admin,name=SpringApplication-" + service.name());
        return props;
    }

    /** 逆序关闭已启动的上下文；单个关闭失败不影响其余，尽最大努力释放端口。 */
    private static void shutdown(List<ConfigurableApplicationContext> started) {
        for (int i = started.size() - 1; i >= 0; i--) {
            try {
                started.get(i).close();
            } catch (Throwable closeEx) {
                System.err.printf("[nebula-all] 关闭上下文失败: %s%n", closeEx.getMessage());
            }
        }
    }

    /**
     * 解析要启动的服务列表：环境变量 {@code NEBULA_SERVICES} 优先，其次系统属性 {@code nebula.services}，
     * 都缺省时启动全部。未知服务名直接报错，避免拼写错误静默少起一个服务。
     */
    private static List<String> resolveEnabledServices() {
        String raw = System.getenv("NEBULA_SERVICES");
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("nebula.services");
        }
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>(SERVICES.keySet());
        }

        List<String> requested = Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        List<String> unknown = requested.stream()
                .filter(s -> !SERVICES.containsKey(s))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "未知服务名 " + unknown + "，可选值: " + SERVICES.keySet());
        }
        // 按 SERVICES 的固定顺序返回，忽略用户书写顺序，保证 manager 优先。
        return SERVICES.keySet().stream()
                .filter(requested::contains)
                .toList();
    }
}
