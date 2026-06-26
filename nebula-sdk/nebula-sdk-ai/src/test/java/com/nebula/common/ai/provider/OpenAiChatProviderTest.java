package com.nebula.common.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link OpenAiChatProvider} 单测：校验 options 扩展参数透传到请求体，且不覆盖标准字段。
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
            byte[] resp = ("{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                    + "\"model\":\"m\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
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
}
