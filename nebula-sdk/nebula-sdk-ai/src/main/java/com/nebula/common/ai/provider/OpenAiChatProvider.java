package com.nebula.common.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AbstractAiProvider;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.exception.AiException;
import com.nebula.common.ai.properties.AiProperties;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI兼容Chat服务提供商
 * 通过 httpclient5 调用 OpenAI 协议的 {@code /chat/completions} 接口，把响应中的
 * {@code choices[0].message.content} 归一化为统一响应的 {@code content} 字段，供上层Agent消费。
 *
 * <p>仅实现同步对话；{@link #doStream} 退化为单次完整响应回调，暂不做SSE增量解析。
 *
 * @author nebula
 */
public class OpenAiChatProvider extends AbstractAiProvider {

    /**
     * 响应体在错误信息中的最大保留长度，避免日志过长
     */
    private static final int ERROR_BODY_LIMIT = 500;

    private final AiProperties.OpenAi config;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RequestConfig requestConfig;

    public OpenAiChatProvider(AiProperties.OpenAi config, CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(config.getTimeoutMs()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.getTimeoutMs()))
                .build();
    }

    @Override
    public String getProvider() {
        return config.getProvider();
    }

    @Override
    protected Map<String, Object> buildRequest(AiRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.getModel() != null ? request.getModel() : config.getModel());
        payload.put("messages", resolveMessages(request));

        Double temperature = request.getTemperature() != null ? request.getTemperature() : config.getTemperature();
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : config.getMaxTokens();
        if (maxTokens != null) {
            payload.put("max_tokens", maxTokens);
        }
        Double topP = request.getTopP() != null ? request.getTopP() : config.getTopP();
        if (topP != null) {
            payload.put("top_p", topP);
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            payload.put("stop", request.getStop());
        }
        payload.put("stream", false);
        return payload;
    }

    /**
     * 解析下发给厂商的消息列表：优先使用请求中的消息历史，否则用单条提示词构造用户消息。
     *
     * @param request 请求参数
     * @return 消息列表
     */
    private List<Map<String, Object>> resolveMessages(AiRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            return request.getMessages();
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", request.getPrompt() == null ? "" : request.getPrompt());
        return List.of(message);
    }

    @Override
    protected Map<String, Object> execute(AiRequest request, Map<String, Object> payload) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new AiException("未配置AI ApiKey（nebula.ai.openai.api-key）");
        }
        String url = resolveEndpoint();
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new AiException("构建AI请求体失败: " + e.getMessage(), e);
        }

        HttpPost post = new HttpPost(url);
        post.setConfig(requestConfig);
        post.setHeader("Authorization", "Bearer " + config.getApiKey());
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        try {
            return httpClient.execute(post, response -> {
                String responseBody = readBody(response.getEntity());
                int code = response.getCode();
                if (code < 200 || code >= 300) {
                    throw new AiException("AI调用失败, status=" + code + ", body=" + brief(responseBody));
                }
                try {
                    return objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception e) {
                    throw new AiException("解析AI响应失败: " + e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            throw new AiException("AI调用IO异常: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseResponse(AiRequest request, Map<String, Object> rawResponse) {
        Map<String, Object> result = new LinkedHashMap<>();
        String content = "";
        if (rawResponse != null) {
            Object choicesObj = rawResponse.get("choices");
            if (choicesObj instanceof List<?> choices && !choices.isEmpty()
                    && choices.get(0) instanceof Map<?, ?> choice) {
                if (choice.get("message") instanceof Map<?, ?> message && message.get("content") != null) {
                    content = String.valueOf(message.get("content"));
                }
                if (choice.get("finish_reason") != null) {
                    result.put("finishReason", choice.get("finish_reason"));
                }
            }
            if (rawResponse.get("model") != null) {
                result.put("model", rawResponse.get("model"));
            }
            if (rawResponse.get("usage") instanceof Map) {
                result.put("usage", rawResponse.get("usage"));
            }
        }
        result.put("content", content);
        result.put("role", "assistant");
        return result;
    }

    @Override
    protected void doStream(AiRequest request, AiCallback callback) {
        // 暂未实现SSE增量；退化为单次完整调用后整体回调，保证流式接口语义可用。
        try {
            Map<String, Object> response = parseResponse(request, execute(request, buildRequest(request)));
            callback.onDelta(String.valueOf(response.getOrDefault("content", "")), response);
            callback.onComplete(response);
        } catch (RuntimeException e) {
            callback.onError(e);
            throw e;
        }
    }

    /**
     * 拼接 chat/completions 端点地址，容忍 baseUrl 末尾斜杠。
     *
     * @return 完整端点地址
     */
    private String resolveEndpoint() {
        String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/chat/completions";
    }

    /**
     * 读取响应体文本
     *
     * @param entity 响应实体
     * @return 响应体文本；实体为空时返回空串
     */
    private String readBody(HttpEntity entity) {
        if (entity == null) {
            return "";
        }
        try {
            return EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AiException("读取AI响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 截断响应体用于错误信息
     *
     * @param body 响应体
     * @return 截断后的文本
     */
    private String brief(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > ERROR_BODY_LIMIT ? body.substring(0, ERROR_BODY_LIMIT) + "..." : body;
    }
}
