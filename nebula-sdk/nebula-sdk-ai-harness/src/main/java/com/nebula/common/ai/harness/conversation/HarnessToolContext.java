package com.nebula.common.ai.harness.conversation;

import com.nebula.common.ai.flow.ToolContext;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harness 工具上下文。
 *
 * <p>认证信息来自不可变 {@link HarnessCallContext}，不会依赖请求线程的 ThreadLocal；attributes 仅在当前
 * Harness 循环内共享，用于连续工具调用传递临时产物。
 *
 * @author nebula
 */
public final class HarnessToolContext implements ToolContext {

    private final HarnessCallContext callContext;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public HarnessToolContext(HarnessCallContext callContext) {
        if (callContext == null) {
            throw new IllegalArgumentException("callContext 不能为空");
        }
        this.callContext = callContext;
    }

    public HarnessCallContext callContext() {
        return callContext;
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
        return callContext.userId();
    }

    @Override
    public String conversationId() {
        return callContext.sessionId();
    }
}
