package com.nebula.common.ai.flow;

import java.util.Map;

/**
 * 工具运行上下文（抽象）
 * 抽出工具执行真正依赖的最小能力：读写共享产物 + 归属用户/会话。把工具从「编排专属上下文」中解耦，
 * 使同一个 {@link ToolDefinition} 既能在 flow 编排节点里运行（由 {@code OrchestrationContext} 承载），
 * 也能被模型 function-calling 闭环直接驱动（由 {@code SimpleToolContext} 承载）。
 *
 * <p>{@code OrchestrationContext} 原生实现本接口，因此现有流程工具零改动；
 * function-calling 场景使用轻量实现，仅携带用户/会话身份与一份临时产物表。
 *
 * @author nebula
 */
public interface ToolContext {

    /**
     * 读取一个产物
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    Object get(String key);

    /**
     * 以字符串读取一个产物
     *
     * @param key 键
     * @return 字符串值，不存在返回 null
     */
    String getString(String key);

    /**
     * 写入一个产物（null 键或值忽略）
     *
     * @param key   键
     * @param value 值
     * @return 当前上下文，便于链式写入
     */
    ToolContext put(String key, Object value);

    /**
     * 是否包含指定产物
     *
     * @param key 键
     * @return 是否存在
     */
    boolean contains(String key);

    /**
     * 获取全部产物（只读视图）
     *
     * @return 产物映射
     */
    Map<String, Object> attributes();

    /**
     * 归属用户ID
     *
     * @return 用户ID，可空
     */
    String userId();

    /**
     * 关联会话ID
     *
     * @return 会话ID，可空
     */
    String conversationId();
}
