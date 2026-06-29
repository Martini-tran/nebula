package com.nebula.common.ai.flow.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpToolDefinition} 单测：用 JDK 内置 HttpServer 起桩，覆盖 GET/POST、查询参数、5xx 重试、整体超时。
 *
 * @author nebula
 */
class HttpToolDefinitionTest {

    private HttpServer server;

    private int port;

    private HttpToolDefinition tool;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();

        HttpToolProperties props = new HttpToolProperties();
        props.setMaxRetries(2);
        props.setRetryBackoffMs(10);
        props.setCallTimeoutMs(2000);
        tool = new HttpToolDefinition(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (tool != null) {
            tool.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    @SuppressWarnings("unchecked")
    @Test
    void GET请求带查询参数() {
        server.createContext("/ping", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            byte[] resp = ("ok:" + query).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        Map<String, Object> params = Map.of(
                "url", base() + "/ping",
                "method", "GET",
                "query", Map.of("q", "hello world"));
        Map<String, Object> result = (Map<String, Object>) tool.invoke(params, new OrchestrationContext());

        assertEquals(200, result.get("status"));
        assertTrue(String.valueOf(result.get("body")).contains("q=hello+world"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void POST请求发送JSON体() {
        StringBuilder received = new StringBuilder();
        server.createContext("/echo", exchange -> {
            byte[] in = exchange.getRequestBody().readAllBytes();
            received.append(new String(in, StandardCharsets.UTF_8));
            byte[] resp = "created".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        Map<String, Object> params = Map.of(
                "url", base() + "/echo",
                "method", "POST",
                "body", Map.of("name", "nebula"));
        Map<String, Object> result = (Map<String, Object>) tool.invoke(params, new OrchestrationContext());

        assertEquals(201, result.get("status"));
        assertTrue(received.toString().contains("\"name\":\"nebula\""));
    }

    @SuppressWarnings("unchecked")
    @Test
    void 服务端5xx触发重试后成功() {
        AtomicInteger hits = new AtomicInteger(0);
        server.createContext("/flaky", exchange -> {
            int n = hits.incrementAndGet();
            int status = n < 3 ? 503 : 200;
            byte[] resp = ("attempt" + n).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        Map<String, Object> params = Map.of("url", base() + "/flaky", "method", "GET");
        Map<String, Object> result = (Map<String, Object>) tool.invoke(params, new OrchestrationContext());

        // maxRetries=2 → 共 3 次尝试，第 3 次返回 200
        assertEquals(200, result.get("status"));
        assertEquals(3, hits.get());
    }

    @Test
    void 整体超时被中断() {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                return;
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        Map<String, Object> params = Map.of(
                "url", base() + "/slow",
                "method", "GET",
                "timeoutMs", 300,
                "maxRetries", 0);

        assertThrows(OrchestrationException.class,
                () -> tool.invoke(params, new OrchestrationContext()));
    }

    @Test
    void 缺少url抛异常() {
        assertThrows(OrchestrationException.class,
                () -> tool.invoke(Map.of("method", "GET"), new OrchestrationContext()));
    }
}
