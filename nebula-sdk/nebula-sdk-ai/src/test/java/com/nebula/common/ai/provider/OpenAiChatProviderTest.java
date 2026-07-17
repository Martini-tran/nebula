package com.nebula.common.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.properties.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link OpenAiChatProvider} 单测：校验 options 扩展参数透传到请求体不覆盖标准字段，
 * 以及流式调用的 SSE 增量解析（逐片回调 + 内容聚合 + 畸形片段容错）。
 *
 * @author nebula
 */
class OpenAiChatProviderTest {

    private HttpServer server;

    private volatile String capturedBody;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                capturedBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            // 按请求体中的 stream 标记决定返回同步 JSON 还是 SSE 流
            if (capturedBody != null && capturedBody.contains("\"stream\":true")) {
                writeSse(exchange);
                return;
            }
            byte[] resp = ("{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                    + "\"model\":\"m\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
    }

    /**
     * 模拟 OpenAI 流式响应：三个内容片段 + 一个畸形片段 + 结束标记
     */
    private void writeSse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (java.io.OutputStream out = exchange.getResponseBody()) {
            String[] lines = {
                    "data: {\"model\":\"m\",\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n",
                    ": heartbeat\n\n",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n",
                    "data: {oops-broken-json\n\n",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"呀\"},\"finish_reason\":\"stop\"}],"
                            + "\"usage\":{\"total_tokens\":9}}\n\n",
                    "data: [DONE]\n\n",
            };
            for (String line : lines) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
        exchange.close();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void options扩展参数透传且不覆盖标准字段() throws Exception {
        AiProperties.OpenAi config = new AiProperties.OpenAi();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setApiKey("test-key");
        config.setModel("gpt-test");

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            OpenAiChatProvider provider = new OpenAiChatProvider(config, httpClient, objectMapper);
            AiRequest request = new AiRequest("你好");
            request.getOptions().put("response_format", "json_object");
            request.getOptions().put("stream", true); // 不应覆盖标准 stream=false

            Map<String, Object> result = provider.chat(request);
            assertEquals("ok", result.get("content"));

            Map<String, Object> payload = objectMapper.readValue(capturedBody, new TypeReference<>() {
            });
            assertEquals("json_object", payload.get("response_format"));
            assertEquals(false, payload.get("stream"));
            assertFalse((Boolean) java.util.Optional.ofNullable(payload.get("stream")).orElse(false));
        }
    }

    @Test
    void 流式调用逐片回调并聚合完整内容() throws Exception {
        AiProperties.OpenAi config = new AiProperties.OpenAi();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setApiKey("test-key");
        config.setModel("gpt-test");

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            OpenAiChatProvider provider = new OpenAiChatProvider(config, httpClient, objectMapper);

            List<String> deltas = new ArrayList<>();
            AtomicReference<Map<String, Object>> completed = new AtomicReference<>();
            AtomicReference<Throwable> failed = new AtomicReference<>();

            provider.stream(new AiRequest("你好"), new AiCallback() {
                @Override
                public void onDelta(String content, Map<String, Object> chunk) {
                    deltas.add(content);
                }

                @Override
                public void onComplete(Map<String, Object> response) {
                    completed.set(response);
                }

                @Override
                public void onError(Throwable throwable) {
                    failed.set(throwable);
                }
            });

            assertNull(failed.get());
            // 逐片回调：三个内容片段，畸形片段与心跳被跳过，role-only 片段不产生 delta
            assertEquals(List.of("你", "好", "呀"), deltas);

            Map<String, Object> response = completed.get();
            assertNotNull(response);
            assertEquals("你好呀", response.get("content"));
            assertEquals("assistant", response.get("role"));
            assertEquals("m", response.get("model"));
            assertEquals("stop", response.get("finishReason"));
            assertNotNull(response.get("usage"));

            // 请求体确实下发了 stream:true
            Map<String, Object> payload = objectMapper.readValue(capturedBody, new TypeReference<>() {
            });
            assertEquals(true, payload.get("stream"));
        }
    }
}
