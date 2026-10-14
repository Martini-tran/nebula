package com.nebula.space.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 把 MCP 无状态服务端接到普通的 Spring MVC 控制器上
 *
 * <p>SDK 自带的 {@code HttpServletStatelessServerTransport} 是一个独立的 Servlet，走不到 Spring MVC 的拦截器，
 * API Key 守卫（默认拒绝、授权范围、把 key 的所属用户写入 UserContext）在那条路上不生效。
 * 这里只负责 JSON-RPC 报文的收发，请求由 {@link SpaceMcpController} 接收，鉴权与其它接口一致。</p>
 *
 * <p>服务端开了 immediateExecution，工具在请求线程上同步执行，才取得到 UserContext 与 Sa-Token 的请求上下文。</p>
 */
@Slf4j
public class SpaceMcpTransport implements McpStatelessServerTransport {

    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    /**
     * 报文解析失败时没有 id 可回，JSONRPCResponse 又不允许 id 为空，只能直接写
     */
    private static final String PARSE_ERROR_BODY =
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"无法解析的 JSON-RPC 报文\"}}";

    private final McpJsonMapper jsonMapper;

    private volatile McpStatelessServerHandler handler;

    public SpaceMcpTransport(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.empty();
    }

    /**
     * 处理一条客户端消息：请求回 JSON-RPC 响应，通知回 202
     */
    public ResponseEntity<String> handle(String body) {
        McpSchema.JSONRPCMessage message;
        try {
            message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body == null ? "" : body);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.badRequest().contentType(JSON_UTF8).body(PARSE_ERROR_BODY);
        }

        McpTransportContext context = McpTransportContext.EMPTY;
        if (message instanceof McpSchema.JSONRPCRequest request) {
            McpSchema.JSONRPCResponse response = handler.handleRequest(context, request)
                    .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
                    // 找不到方法等错误 SDK 以 Mono.error 抛出，这里统一转成 JSON-RPC 错误
                    .onErrorResume(e -> Mono.just(McpSchema.JSONRPCResponse.error(request.id(), toError(e))))
                    .block();
            return ResponseEntity.ok().contentType(JSON_UTF8).body(write(response));
        }
        if (message instanceof McpSchema.JSONRPCNotification notification) {
            handler.handleNotification(context, notification)
                    .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
                    .onErrorResume(e -> {
                        log.warn("MCP 通知处理失败: method={}, msg={}", notification.method(), e.getMessage());
                        return Mono.empty();
                    })
                    .block();
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        // 客户端发来的响应：无状态服务端不会向客户端发请求，收下即可
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private McpSchema.JSONRPCResponse.JSONRPCError toError(Throwable e) {
        if (e instanceof McpError mcpError && mcpError.getJsonRpcError() != null) {
            return mcpError.getJsonRpcError();
        }
        log.error("MCP 请求处理失败", e);
        return new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INTERNAL_ERROR, "服务器内部错误");
    }

    private String write(McpSchema.JSONRPCResponse response) {
        try {
            return jsonMapper.writeValueAsString(response);
        } catch (IOException e) {
            throw new IllegalStateException("MCP 响应序列化失败", e);
        }
    }
}
