package com.nebula.common.ai.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 embedding 提供者
 * 走独立配置 {@code nebula.ai.embedding.*}（与聊天模型解耦），复用 {@code aiHttpClient} 连接池，POST OpenAI 兼容
 * {@code {base-url}/embeddings}，解析 {@code data[].embedding} 按 {@code index} 对齐回 {@link List}。
 *
 * <p>按 {@code batchSize} 分批、单批 {@code maxRetries} 重试、请求超时 {@code timeoutMs}。维度以配置 {@code dimension}
 * 为准（须与建库维度一致），并在请求体透传 {@code dimensions} 让支持的模型（如 text-embedding-3-*）按维截断。
 *
 * @author nebula
 */
@Slf4j
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    /**
     * 错误响应体在日志中的最大保留长度
     */
    private static final int ERROR_BODY_LIMIT = 500;

    private final AiProperties.Embedding config;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiEmbeddingProvider(AiProperties.Embedding config,
                                   CloseableHttpClient httpClient,
                                   ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String code() {
        return config.getCode();
    }

    @Override
    public int dimension() {
        return config.getDimension();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, config.getBatchSize());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batch = texts.subList(start, end);
            result.addAll(embedBatch(batch));
        }
        return result;
    }

    /**
     * 向量化一批文本（单批带重试）。
     *
     * @param batch 单批文本
     * @return 与入参一一对应的向量
     */
    private List<float[]> embedBatch(List<String> batch) {
        int maxRetries = Math.max(0, config.getMaxRetries());
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return doEmbed(batch);
            } catch (RuntimeException e) {
                last = e;
                log.warn("embedding 调用失败（第 {}/{} 次）：{}", attempt + 1, maxRetries + 1, e.getMessage());
            }
        }
        throw last != null ? last : new IllegalStateException("embedding 调用失败");
    }

    /**
     * 执行一次 embedding 请求：构建 body → POST → 按 index 对齐解析。
     *
     * @param batch 单批文本
     * @return 向量列表（长度 = batch.size，顺序按响应 index 对齐）
     */
    private List<float[]> doEmbed(List<String> batch) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 embedding ApiKey（nebula.ai.embedding.api-key）");
        }
        String url = trimTrailingSlash(config.getBaseUrl()) + "/embeddings";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModel());
        payload.put("input", batch);
        if (config.getDimension() > 0) {
            payload.put("dimensions", config.getDimension());
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构建 embedding 请求体失败: " + e.getMessage(), e);
        }

        HttpPost post = new HttpPost(url);
        post.setConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(config.getTimeoutMs()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.getTimeoutMs()))
                .build());
        post.setHeader("Authorization", "Bearer " + apiKey);
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        try {
            return httpClient.execute(post, response -> {
                String responseBody = response.getEntity() == null
                        ? "" : EntityUtils.toString(response.getEntity());
                int code = response.getCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("embedding 端点返回 " + code + ": " + truncate(responseBody));
                }
                return parseEmbeddings(responseBody, batch.size());
            });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("embedding 请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析响应 {@code data[].embedding}，按 {@code data[].index} 对齐回定长数组（缺失位报错，防脏对齐）。
     *
     * @param responseBody 响应体
     * @param expected     期望向量条数
     * @return 按 index 排好序的向量列表
     */
    private List<float[]> parseEmbeddings(String responseBody, int expected) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != expected) {
                throw new IllegalStateException("embedding 响应条数不匹配，期望 " + expected + " 实得 " + data.size());
            }
            float[][] ordered = new float[expected][];
            for (JsonNode item : data) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= expected) {
                    throw new IllegalStateException("embedding 响应 index 越界: " + index);
                }
                JsonNode vec = item.path("embedding");
                if (!vec.isArray()) {
                    throw new IllegalStateException("embedding 响应缺少 embedding 数组");
                }
                float[] arr = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    arr[i] = (float) vec.get(i).asDouble();
                }
                ordered[index] = arr;
            }
            List<float[]> result = new ArrayList<>(expected);
            for (int i = 0; i < expected; i++) {
                if (ordered[i] == null) {
                    throw new IllegalStateException("embedding 响应缺少 index=" + i + " 的向量");
                }
                result.add(ordered[i]);
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析 embedding 响应失败: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= ERROR_BODY_LIMIT ? s : s.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}
