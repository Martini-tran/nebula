package com.nebula.common.ai.agent.tool;

import com.nebula.common.ai.flow.ToolContext;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量工具上下文（function-calling 场景）
 * 在模型 function-calling 闭环中承载 {@link ToolContext}：仅携带归属用户/会话身份与一份临时产物表，
 * 供工具在一次工具调用循环内读写共享态。与编排无关，区别于流程编排使用的 {@code OrchestrationContext}。
 *
 * @author nebula
 */
public class SimpleToolContext implements ToolContext {

    private final String userId;

    private final String conversationId;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public SimpleToolContext(String userId, String conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
    }

    @Override
    public Object get(String key) {
        return key == null ? null : attributes.get(key);
    }

    @Override
    public String getString(String key) {
        Object value = get(key);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public ToolContext put(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
        return this;
    }

    @Override
    public boolean contains(String key) {
        return key != null && attributes.containsKey(key);
    }

    @Override
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public String conversationId() {
        return conversationId;
    }
}
