package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.util.AiTemplateUtils;

import java.util.Map;

/**
 * AI服务提供商抽象实现
 *
 * @author nebula
 */
public abstract class AbstractAiProvider implements AiProvider {

    @Override
    public Map<String, Object> chat(AiRequest request) {
        prepareRequest(request);
        Map<String, Object> payload = buildRequest(request);
        Map<String, Object> rawResponse = execute(request, payload);
        return parseResponse(request, rawResponse);
    }

    @Override
    public void stream(AiRequest request, AiCallback callback) {
        prepareRequest(request);
        callback.onStart(request);
        doStream(request, callback);
    }

    /**
     * 准备请求参数
     *
     * @param request 请求参数
     */
    protected void prepareRequest(AiRequest request) {
        if (request == null) {
            return;
        }
        request.setPrompt(AiTemplateUtils.render(request.getPrompt(), request.getVariables()));
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return;
        }
        request.getMessages().forEach(message -> replaceMessageContent(message, request.getVariables()));
    }

    private void replaceMessageContent(Map<String, Object> message, Map<String, Object> variables) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Object content = message.get("content");
        if (content instanceof String text) {
            message.put("content", AiTemplateUtils.render(text, variables));
        }
    }

    /**
     * 构建厂商请求参数
     *
     * @param request 请求参数
     * @return 厂商请求参数
     */
    protected abstract Map<String, Object> buildRequest(AiRequest request);

    /**
     * 执行厂商调用
     *
     * @param request 请求参数
     * @param payload 厂商请求参数
     * @return 厂商原始响应
     */
    protected abstract Map<String, Object> execute(AiRequest request, Map<String, Object> payload);

    /**
     * 解析厂商响应结果
     *
     * @param request     请求参数
     * @param rawResponse 厂商原始响应
     * @return 响应结果
     */
    protected abstract Map<String, Object> parseResponse(AiRequest request, Map<String, Object> rawResponse);

    /**
     * 执行厂商流式调用
     *
     * @param request  请求参数
     * @param callback 回调处理器
     */
    protected abstract void doStream(AiRequest request, AiCallback callback);
}
