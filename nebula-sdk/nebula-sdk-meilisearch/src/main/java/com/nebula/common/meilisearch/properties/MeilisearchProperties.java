package com.nebula.common.meilisearch.properties;

import com.nebula.common.meilisearch.api.IndexConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Meilisearch 配置属性
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.meilisearch")
public class MeilisearchProperties {

    /**
     * 是否启用 Meilisearch
     */
    private boolean enabled = false;

    /**
     * 服务地址，例如 http://localhost:7700
     */
    private String host = "http://localhost:7700";

    /**
     * API Key，部分场景下可为空
     */
    private String apiKey;

    /**
     * 请求超时时间（毫秒），不设置则使用 SDK 默认值
     */
    private Integer requestTimeout;

    /**
     * 等待异步任务完成的超时时间（毫秒）
     */
    private int taskTimeoutMs = 5000;

    /**
     * 等待异步任务的轮询间隔（毫秒）
     */
    private int taskIntervalMs = 50;

    /**
     * 是否在写操作（添加、更新、删除）后阻塞等待任务完成
     */
    private boolean waitForTask = false;

    /**
     * 预定义的索引配置，启动时会自动创建
     */
    private Map<String, IndexConfig> indexes = new HashMap<>();
}
