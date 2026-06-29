package com.nebula.common.ai.flow.tool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 工具配置属性
 * 定义级配置（连接池 + 专用线程池 + 默认超时/重试），与调用级参数（url/method/headers/...，由流程节点
 * {@code nodeConfig} 逐次传入）分离。线程池为 HTTP 工具专用，使其调用与编排主线程、其它工具隔离，
 * 并据此对单次调用施加「整体超时」（线程级中断兜底，区别于 HttpClient 的连接/读取超时）。
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.ai.tool.http")
public class HttpToolProperties {

    /**
     * 连接池最大连接数
     */
    private int maxConnections = 50;

    /**
     * 每个路由（目标主机）最大连接数
     */
    private int maxConnectionsPerRoute = 10;

    /**
     * 连接建立超时（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 响应读取超时（毫秒）
     */
    private int responseTimeoutMs = 30000;

    /**
     * 单次调用整体超时（毫秒）：线程池任务的最长等待，超时即中断该次请求。
     * 应 &ge; connectTimeoutMs + responseTimeoutMs，作为兜底上限。
     */
    private int callTimeoutMs = 40000;

    /**
     * 失败重试次数（不含首次）。仅对 IO 异常/超时与 5xx 重试，4xx 不重试。
     */
    private int maxRetries = 2;

    /**
     * 重试间隔（毫秒）
     */
    private long retryBackoffMs = 500;

    /**
     * 专用线程池核心线程数
     */
    private int corePoolSize = 4;

    /**
     * 专用线程池最大线程数
     */
    private int maxPoolSize = 16;

    /**
     * 专用线程池队列容量
     */
    private int queueCapacity = 128;

    /**
     * 空闲线程存活（秒）
     */
    private long keepAliveSeconds = 60;
}
