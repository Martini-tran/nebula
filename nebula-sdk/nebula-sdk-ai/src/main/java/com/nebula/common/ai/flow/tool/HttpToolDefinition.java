package com.nebula.common.ai.flow.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.orchestration.OrchestrationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTTP 请求工具
 * 流程节点可调用的通用 HTTP 客户端：支持请求方式、请求头、查询参数、请求体、单次超时与失败重试。
 * 调用在 <b>专用线程池</b> 中执行，与编排主线程隔离，并据 {@code callTimeoutMs} 施加「整体超时」
 * （线程级兜底，叠加于 HttpClient 的连接/读取超时之上，确保任何阶段卡住都能在上限内返回）。
 *
 * <p>定义级配置（连接池/线程池/默认超时与重试）见 {@link HttpToolProperties}；调用级参数从入参 {@code params}
 * 读取（由节点 {@code inputMapping} 从上下文映射）。参数键：
 * <ul>
 *   <li>{@code url}（必填）、{@code method}（默认 GET）、{@code contentType}（默认 application/json）</li>
 *   <li>{@code headers}（Map）、{@code query}（Map，拼到 URL）、{@code body}（String 或对象，对象序列化为 JSON）</li>
 *   <li>{@code timeoutMs}、{@code maxRetries}（覆盖定义级默认）</li>
 * </ul>
 * 产物为 {@code {status, headers, body}} 的 Map，写回上下文供下游节点引用。
 *
 * @author nebula
 */
@Slf4j
public class HttpToolDefinition implements ToolDefinition {

    private final HttpToolProperties props;

    private final ObjectMapper objectMapper;

    private final CloseableHttpClient httpClient;

    private final PoolingHttpClientConnectionManager connectionManager;

    private final ThreadPoolExecutor executor;

    public HttpToolDefinition(HttpToolProperties props, ObjectMapper objectMapper) {
        this.props = props == null ? new HttpToolProperties() : props;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.connectionManager = buildConnectionManager(this.props);
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(this.props.getResponseTimeoutMs()))
                        .build())
                .build();
        this.executor = buildExecutor(this.props);
    }

    @Override
    public String code() {
        return "http";
    }

    @Override
    public String name() {
        return "HTTP请求";
    }

    @Override
    public String description() {
        return "向指定 URL 发起 HTTP 请求，支持请求方式/头/查询参数/请求体/超时与失败重试，返回状态码、响应头与响应体";
    }

    @Override
    public String category() {
        return "http";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", prop("string", "请求地址（必填）"));
        properties.put("method", prop("string", "请求方式：GET/POST/PUT/DELETE/PATCH，默认 GET"));
        properties.put("headers", prop("object", "请求头键值对"));
        properties.put("query", prop("object", "查询参数键值对，拼接到 URL"));
        properties.put("body", prop("object", "请求体；字符串原样发送，对象序列化为 JSON"));
        properties.put("contentType", prop("string", "请求体 Content-Type，默认 application/json"));
        properties.put("timeoutMs", prop("integer", "单次调用整体超时（毫秒），覆盖默认"));
        properties.put("maxRetries", prop("integer", "失败重试次数，覆盖默认"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("url"));
        return schema;
    }

    @Override
    public Map<String, Object> resultSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", prop("integer", "HTTP 状态码"));
        properties.put("headers", prop("object", "响应头"));
        properties.put("body", prop("string", "响应体文本"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public int sortNo() {
        return 10;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        Map<String, Object> p = params == null ? Map.of() : params;
        String url = str(p.get("url"));
        if (url == null || url.isBlank()) {
            throw new OrchestrationException("HTTP工具缺少必填参数 url");
        }
        String method = firstNonBlank(str(p.get("method")), "GET").toUpperCase();
        int timeoutMs = intValue(p.get("timeoutMs"), props.getCallTimeoutMs());
        int maxRetries = intValue(p.get("maxRetries"), props.getMaxRetries());

        // 提交到专用线程池，并以 Future 超时施加整体上限（线程级兜底）
        Callable<Map<String, Object>> task = () -> executeWithRetry(url, method, p, maxRetries);
        Future<Map<String, Object>> future = executor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new OrchestrationException("HTTP调用整体超时(" + timeoutMs + "ms): " + url, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new OrchestrationException("HTTP调用失败: " + url + " - " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new OrchestrationException("HTTP调用被中断: " + url, e);
        }
    }

    /**
     * 带重试地执行一次 HTTP 调用：IO 异常/超时与 5xx 重试，4xx 直接返回（不重试）。
     */
    private Map<String, Object> executeWithRetry(String url, String method, Map<String, Object> p, int maxRetries)
            throws Exception {
        int attempts = Math.max(0, maxRetries) + 1;
        Exception lastError = null;
        for (int i = 0; i < attempts; i++) {
            if (i > 0 && props.getRetryBackoffMs() > 0) {
                Thread.sleep(props.getRetryBackoffMs());
            }
            try {
                Map<String, Object> result = doExecute(url, method, p);
                int status = (int) result.get("status");
                if (status >= 500 && i < attempts - 1) {
                    log.warn("HTTP工具 {} 返回 {}，第 {}/{} 次重试", url, status, i + 1, attempts - 1);
                    continue;
                }
                return result;
            } catch (IOException e) {
                lastError = e;
                log.warn("HTTP工具 {} IO异常({})，第 {}/{} 次", url, e.getMessage(), i + 1, attempts - 1);
            }
        }
        throw lastError == null ? new IOException("HTTP调用失败: " + url) : lastError;
    }

    /**
     * 实际发起一次请求：拼查询参数、设头、设体、解析响应为 {status, headers, body}。
     */
    private Map<String, Object> doExecute(String url, String method, Map<String, Object> p) throws IOException {
        ClassicRequestBuilder builder = ClassicRequestBuilder.create(method).setUri(buildUri(url, p.get("query")));
        applyHeaders(builder, p.get("headers"));
        applyBody(builder, method, p);

        ClassicHttpRequest request = builder.build();
        return httpClient.execute(request, response -> {
            String body = response.getEntity() == null ? ""
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            Map<String, Object> headers = new LinkedHashMap<>();
            for (Header h : response.getHeaders()) {
                headers.put(h.getName(), h.getValue());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.getCode());
            result.put("headers", headers);
            result.put("body", body);
            return result;
        });
    }

    /**
     * 拼接查询参数到 URL。
     */
    @SuppressWarnings("unchecked")
    private String buildUri(String url, Object query) {
        if (!(query instanceof Map<?, ?> map) || map.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(str(e.getValue())));
            first = false;
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void applyHeaders(ClassicRequestBuilder builder, Object headers) {
        if (headers instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.setHeader(k, str(v));
                }
            });
        }
    }

    /**
     * 设置请求体：字符串原样发送，对象序列化为 JSON；GET/DELETE 无体方法忽略。
     */
    private void applyBody(ClassicRequestBuilder builder, String method, Map<String, Object> p) {
        Object body = p.get("body");
        if (body == null || "GET".equals(method) || "HEAD".equals(method)) {
            return;
        }
        String contentType = firstNonBlank(str(p.get("contentType")), ContentType.APPLICATION_JSON.getMimeType());
        String payload;
        if (body instanceof String s) {
            payload = s;
        } else {
            try {
                payload = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                throw new OrchestrationException("HTTP工具请求体序列化失败: " + e.getMessage(), e);
            }
        }
        builder.setEntity(new StringEntity(payload, ContentType.parse(contentType)));
    }

    private PoolingHttpClientConnectionManager buildConnectionManager(HttpToolProperties props) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(props.getMaxConnections())
                .setMaxConnPerRoute(props.getMaxConnectionsPerRoute())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeoutMs()))
                        .build())
                .build();
    }

    private ThreadPoolExecutor buildExecutor(HttpToolProperties props) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                props.getCorePoolSize(),
                props.getMaxPoolSize(),
                props.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(props.getQueueCapacity()),
                new HttpToolThreadFactory(),
                // 队列满时由调用线程直接执行，避免静默丢弃请求
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * 释放连接池与线程池。由装配方在 Bean 销毁时调用。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("关闭HTTP工具客户端异常: {}", e.getMessage());
        }
        connectionManager.close();
    }

    private static Map<String, Object> prop(String type, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("description", desc);
        return m;
    }

    private static String urlEncode(String v) {
        return v == null ? "" : java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static int intValue(Object v, int defaultValue) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                // 落回默认
            }
        }
        return defaultValue;
    }

    /**
     * HTTP 工具专用线程工厂：命名便于排障，全部守护线程不阻塞 JVM 退出。
     */
    private static final class HttpToolThreadFactory implements java.util.concurrent.ThreadFactory {

        private final java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ai-http-tool-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
