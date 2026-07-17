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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI兼容Chat服务提供商
 * 通过 httpclient5 调用 OpenAI 协议的 {@code /chat/completions} 接口，把响应中的
 * {@code choices[0].message.content} 归一化为统一响应的 {@code content} 字段，供上层Agent消费。
 *
 * <p>同步对话见 {@link #chat}；{@link #doStream} 下发 {@code stream:true} 并逐行解析 SSE 增量，
 * 通过 {@link AiCallback#onDelta} 吐出片段，结束后以聚合内容回调 {@link AiCallback#onComplete}。
 *
 * @author nebula
 */
public class OpenAiChatProvider extends AbstractAiProvider {

    /**
     * 响应体在错误信息中的最大保留长度，避免日志过长
     */
    private static final int ERROR_BODY_LIMIT = 500;

    /**
     * SSE 数据行前缀
     */
    private static final String SSE_DATA_PREFIX = "data:";

    /**
     * SSE 流结束标记
     */
    private static final String SSE_DONE = "[DONE]";

    private final AiProperties.OpenAi config;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiChatProvider(AiProperties.OpenAi config, CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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
        // 工具声明：非空时下发 tools，让模型可自主发起工具调用；tool_choice 控制选择策略，为空则不下发（沿用厂商默认 auto）
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            payload.put("tools", request.getTools());
            if (request.getToolChoice() != null) {
                payload.put("tool_choice", request.getToolChoice());
            }
        }
        payload.put("stream", request.isStream());
        // 透传扩展参数：response_format / frequency_penalty / presence_penalty / seed 等厂商私有参数
        // 经配置下发；putIfAbsent 保证不覆盖上面已显式构建的标准字段
        if (request.getOptions() != null) {
            request.getOptions().forEach((key, value) -> {
                if (key != null && value != null) {
                    payload.putIfAbsent(key, value);
                }
            });
        }
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
        String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
                ? request.getApiKey() : config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException("未配置AI ApiKey（nebula.ai.openai.api-key 或节点模型档案）");
        }
        String url = resolveEndpoint(request);
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new AiException("构建AI请求体失败: " + e.getMessage(), e);
        }

        HttpPost post = new HttpPost(url);
        post.setConfig(resolveRequestConfig(request));
        post.setHeader("Authorization", "Bearer " + apiKey);
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
                if (choice.get("message") instanceof Map<?, ?> message) {
                    if (message.get("content") != null) {
                        content = String.valueOf(message.get("content"));
                    }
                    // 工具调用归一化：把 message.tool_calls 解析为统一的 {id,name,arguments} 列表，
                    // 并原样保留整条 assistant message（含 tool_calls 原文），供 ToolCallingService 回灌时
                    // 按 OpenAI 约定先追加 assistant 消息再追加 tool 结果。
                    List<Map<String, Object>> toolCalls = parseToolCalls(message.get("tool_calls"));
                    if (!toolCalls.isEmpty()) {
                        result.put("toolCalls", toolCalls);
                        result.put("assistantMessage", message);
                    }
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

    /**
     * 解析厂商响应中的 {@code tool_calls} 为归一化列表。
     * 每个元素含 {@code id}（回灌时作 tool_call_id）、{@code name}（目标工具编码）、{@code arguments}（入参 JSON 字符串）。
     * 容忍缺失字段，跳过结构非法的项。
     *
     * @param toolCallsObj 厂商 {@code message.tool_calls}
     * @return 归一化的工具调用列表，无则空列表
     */
    private List<Map<String, Object>> parseToolCalls(Object toolCallsObj) {
        List<Map<String, Object>> normalized = new java.util.ArrayList<>();
        if (!(toolCallsObj instanceof List<?> list)) {
            return normalized;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> call)) {
                continue;
            }
            Object function = call.get("function");
            if (!(function instanceof Map<?, ?> fn)) {
                continue;
            }
            Object name = fn.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", call.get("id") == null ? "" : String.valueOf(call.get("id")));
            one.put("name", String.valueOf(name));
            one.put("arguments", fn.get("arguments") == null ? "{}" : String.valueOf(fn.get("arguments")));
            normalized.add(one);
        }
        return normalized;
    }

    @Override
    protected void doStream(AiRequest request, AiCallback callback) {
        request.setStream(true);
        Map<String, Object> payload = buildRequest(request);

        String apiKey = request.getApiKey() != null && !request.getApiKey().isBlank()
                ? request.getApiKey() : config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            AiException error = new AiException("未配置AI ApiKey（nebula.ai.openai.api-key 或节点模型档案）");
            callback.onError(error);
            throw error;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            AiException error = new AiException("构建AI流式请求体失败: " + e.getMessage(), e);
            callback.onError(error);
            throw error;
        }

        HttpPost post = new HttpPost(resolveEndpoint(request));
        post.setConfig(resolveStreamRequestConfig(request));
        post.setHeader("Authorization", "Bearer " + apiKey);
        post.setHeader("Accept", "text/event-stream");
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        try {
            Map<String, Object> response = httpClient.execute(post, httpResponse -> {
                int code = httpResponse.getCode();
                if (code < 200 || code >= 300) {
                    throw new AiException("AI流式调用失败, status=" + code
                            + ", body=" + brief(readBody(httpResponse.getEntity())));
                }
                return readStream(httpResponse.getEntity(), callback);
            });
            callback.onComplete(response);
        } catch (AiException e) {
            callback.onError(e);
            throw e;
        } catch (IOException e) {
            AiException error = new AiException("AI流式调用IO异常: " + e.getMessage(), e);
            callback.onError(error);
            throw error;
        }
    }

    /**
     * 逐行读取 SSE 响应体，解析增量片段并回调，最终聚合为统一响应。
     * 按 OpenAI 流式约定：每行形如 {@code data: {...}}，{@code data: [DONE]} 表示结束；
     * 空行为事件分隔符，非 {@code data:} 前缀行（注释/心跳）忽略。
     *
     * @param entity   响应实体
     * @param callback 回调处理器
     * @return 聚合后的统一响应，含拼接完成的 content
     */
    private Map<String, Object> readStream(HttpEntity entity, AiCallback callback) {
        if (entity == null) {
            throw new AiException("AI流式响应体为空");
        }
        StringBuilder content = new StringBuilder();
        Map<String, Object> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.startsWith(SSE_DATA_PREFIX)) {
                    continue;
                }
                String data = line.substring(SSE_DATA_PREFIX.length()).trim();
                if (SSE_DONE.equals(data)) {
                    break;
                }
                Map<String, Object> chunk;
                try {
                    chunk = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception e) {
                    // 容忍单个畸形片段，不中断整个流
                    continue;
                }
                String delta = extractDelta(chunk, result);
                if (!delta.isEmpty()) {
                    content.append(delta);
                    callback.onDelta(delta, chunk);
                }
            }
        } catch (IOException e) {
            throw new AiException("读取AI流式响应失败: " + e.getMessage(), e);
        }
        result.put("content", content.toString());
        result.put("role", "assistant");
        return result;
    }

    /**
     * 从单个流式片段中提取增量文本，并把 model/finish_reason/usage 收集进聚合结果。
     *
     * @param chunk  厂商流式片段
     * @param result 聚合结果，就地写入元信息
     * @return 增量文本；无增量时返回空串
     */
    private String extractDelta(Map<String, Object> chunk, Map<String, Object> result) {
        if (chunk.get("model") != null) {
            result.putIfAbsent("model", chunk.get("model"));
        }
        if (chunk.get("usage") instanceof Map) {
            result.put("usage", chunk.get("usage"));
        }
        if (!(chunk.get("choices") instanceof List<?> choices)
                || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)) {
            return "";
        }
        if (choice.get("finish_reason") != null) {
            result.put("finishReason", choice.get("finish_reason"));
        }
        if (choice.get("delta") instanceof Map<?, ?> delta && delta.get("content") != null) {
            return String.valueOf(delta.get("content"));
        }
        return "";
    }

    /**
     * 拼接 chat/completions 端点地址，容忍 baseUrl 末尾斜杠。
     * baseUrl 优先取请求携带的运行时覆盖，否则回退全局配置。
     *
     * @param request 请求参数
     * @return 完整端点地址
     */
    private String resolveEndpoint(AiRequest request) {
        String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isBlank()
                ? request.getBaseUrl() : config.getBaseUrl();
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/chat/completions";
    }

    /**
     * 按请求构建超时配置：超时优先取请求携带的运行时覆盖，否则回退全局配置。
     *
     * @param request 请求参数
     * @return 请求配置
     */
    private RequestConfig resolveRequestConfig(AiRequest request) {
        long timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : config.getTimeoutMs();
        return RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();
    }

    /**
     * 按请求构建流式超时配置。
     * 与同步调用不同：流式响应从首字节到结束可远超单次请求超时，故不设 responseTimeout，
     * 仅保留连接获取超时；超时语义由调用方（如 SSE 端点）自行控制。
     *
     * @param request 请求参数
     * @return 请求配置
     */
    private RequestConfig resolveStreamRequestConfig(AiRequest request) {
        long timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : config.getTimeoutMs();
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();
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
