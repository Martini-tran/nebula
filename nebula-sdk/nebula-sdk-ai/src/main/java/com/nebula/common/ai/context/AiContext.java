package com.nebula.common.ai.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AI线程上下文
 *
 * @author nebula
 */
public final class AiContext {

    private static final ThreadLocal<AiInfo> HOLDER = new ThreadLocal<>();

    private AiContext() {
    }

    /**
     * 设置AI线程上下文
     *
     * @param requestId      请求ID
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @param agentCode      Agent功能编码
     * @param provider       服务提供商
     * @param model          模型名称
     */
    public static void set(String requestId, String conversationId, String userId, String agentCode, String provider, String model) {
        set(requestId, conversationId, userId, agentCode, provider, model, Collections.emptyMap());
    }

    /**
     * 设置AI线程上下文
     *
     * @param requestId      请求ID
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @param agentCode      Agent功能编码
     * @param provider       服务提供商
     * @param model          模型名称
     * @param attributes     扩展属性
     */
    public static void set(String requestId,
                           String conversationId,
                           String userId,
                           String agentCode,
                           String provider,
                           String model,
                           Map<String, Object> attributes) {
        HOLDER.set(new AiInfo(requestId, conversationId, userId, agentCode, provider, model, normalize(attributes)));
    }

    /**
     * 获取请求ID
     *
     * @return 请求ID
     */
    public static String getRequestId() {
        AiInfo info = HOLDER.get();
        return info == null ? null : info.requestId();
    }

    /**
     * 获取对话ID
     *
     * @return 对话ID
     */
    public static String getConversationId() {
        AiInfo info = HOLDER.get();
        return info == null ? null : info.conversationId();
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public static String getUserId() {
        AiInfo info = HOLDER.get();
        return info == null ? null : info.userId();
    }

    /**
     * 获取Agent功能编码
     *
     * @return Agent功能编码
     */
    public static String getAgentCode() {
        AiInfo info = HOLDER.get();
        return info == null ? null : info.agentCode();
    }

    /**
     * 获取服务提供商
     *
     * @return 服务提供商
     */
    public static String getProvider() {
        AiInfo info = HOLDER.get();
        return info == null ? null : info.provider();
    }

    /**
     * 获取模型名称
     *
     * @return 模型名称
     */
    public static String getModel() {
        AiInfo info = HOLDER.get();
        return info == null ? null : info.model();
    }

    /**
     * 获取扩展属性
     *
     * @return 扩展属性
     */
    public static Map<String, Object> getAttributes() {
        AiInfo info = HOLDER.get();
        return info == null ? Collections.emptyMap() : info.attributes();
    }

    /**
     * 获取扩展属性值
     *
     * @param key 属性键
     * @return 属性值
     */
    public static Object getAttribute(String key) {
        return getAttributes().get(key);
    }

    /**
     * 获取AI线程上下文
     *
     * @return AI线程上下文
     */
    public static AiInfo get() {
        return HOLDER.get();
    }

    /**
     * 清空AI线程上下文
     */
    public static void clear() {
        HOLDER.remove();
    }

    private static Map<String, Object> normalize(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    public record AiInfo(String requestId,
                         String conversationId,
                         String userId,
                         String agentCode,
                         String provider,
                         String model,
                         Map<String, Object> attributes) {
    }
}
