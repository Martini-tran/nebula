package com.nebula.common.ai.orchestration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编排共享上下文（黑板）
 * 一次编排执行期间在各节点之间传递数据、串联整条流程。{@code attributes} 存放业务产物（节点读写），
 * {@code nodeResults} 按执行顺序记录每个已执行节点的产物/轨迹，供编排器记忆与排障使用。
 *
 * <p>{@code userId} / {@code conversationId} 贯穿全程，使下游记忆按用户隔离、按会话关联。
 *
 * @author nebula
 */
@Getter
@Setter
public class OrchestrationContext {

    /**
     * 归属用户ID
     */
    private String userId;

    /**
     * 关联会话ID（贯穿本次编排，便于记忆关联）
     */
    private String conversationId;

    /**
     * 业务产物：节点间共享的键值数据
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 节点轨迹：按执行顺序记录每个已执行节点的产物
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> nodeResults = new LinkedHashMap<>();

    public OrchestrationContext() {
    }

    public OrchestrationContext(String userId, String conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
    }

    /**
     * 写入一个产物（null 键或值忽略）
     *
     * @param key   键
     * @param value 值
     * @return 当前上下文，便于链式写入
     */
    public OrchestrationContext put(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
        return this;
    }

    /**
     * 读取一个产物
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public Object get(String key) {
        return attributes.get(key);
    }

    /**
     * 按类型读取一个产物
     *
     * @param key  键
     * @param type 期望类型
     * @param <T>  类型参数
     * @return 类型匹配的值，缺失或类型不匹配返回 null
     */
    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /**
     * 以字符串读取一个产物
     *
     * @param key 键
     * @return 字符串值，不存在返回 null
     */
    public String getString(String key) {
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 是否包含指定产物
     *
     * @param key 键
     * @return 是否存在
     */
    public boolean contains(String key) {
        return attributes.containsKey(key);
    }

    /**
     * 记录一个节点的产物/轨迹
     *
     * @param nodeId 节点ID
     * @param result 该节点产物（可为 null 表示无返回）
     */
    public void recordResult(String nodeId, Object result) {
        if (nodeId != null) {
            nodeResults.put(nodeId, result);
        }
    }

    /**
     * 获取全部产物（只读视图）
     *
     * @return 不可变的产物映射
     */
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * 获取节点轨迹（只读视图，保持执行顺序）
     *
     * @return 不可变的节点轨迹映射
     */
    public Map<String, Object> nodeResults() {
        return Collections.unmodifiableMap(nodeResults);
    }
}
