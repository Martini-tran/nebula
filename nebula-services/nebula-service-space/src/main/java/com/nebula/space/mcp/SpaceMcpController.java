package com.nebula.space.mcp;

import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人空间的 MCP 端点（Streamable HTTP，无状态）：Claude Code 等客户端凭 API Key 读写 key 所属用户的个人空间
 *
 * <p>对 key 只开放这一个地址：守卫在这里要求 key 至少具备其中一个授权范围，每个工具要哪个范围再由
 * {@link SpaceMcpTools} 逐个把关。数据按 UserContext 隔离，与 /me 下其它接口一致。</p>
 *
 * <p>类上刻意不标 {@code @SaCheckLogin}：本服务目前没注册 SaInterceptor，那个注解不生效；
 * 将来注册了，它会把凭 key（未登录）的请求全部拦掉。登录态由网关保证。</p>
 */
@RestController
@RequestMapping("/me/mcp")
@RequiredArgsConstructor
public class SpaceMcpController {

    private final SpaceMcpTransport transport;

    /**
     * 客户端发来的 JSON-RPC 消息（初始化、列工具、调工具、通知）
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @SaCheckApiKey(scope = {SpaceMcpScopes.READ, SpaceMcpScopes.REPORT_WRITE, SpaceMcpScopes.WRITE}, mode = SaMode.OR)
    public ResponseEntity<String> message(@RequestBody(required = false) String body) {
        return transport.handle(body);
    }

    /**
     * 服务端推送流：不需要，按协议回 405
     */
    @GetMapping
    @SaCheckApiKey(scope = {SpaceMcpScopes.READ, SpaceMcpScopes.REPORT_WRITE, SpaceMcpScopes.WRITE}, mode = SaMode.OR)
    public ResponseEntity<Void> stream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.POST).build();
    }
}
