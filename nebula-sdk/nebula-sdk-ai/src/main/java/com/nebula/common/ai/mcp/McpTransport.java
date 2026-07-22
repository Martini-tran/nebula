package com.nebula.common.ai.mcp;

import java.util.Map;

/**
 * MCP 传输层（SPI，占位设计）。
 *
 * <p><b>本期仅设计，不实现运行时。</b>抽象与 MCP server 的通信通道，将来按 {@code ai_mcp_server.transport}
 * 落地不同实现：
 * <ul>
 *   <li>{@code streamable-http}：HTTP + SSE 双向流（远程 server）；</li>
 *   <li>{@code sse}：SSE 单向 + HTTP 回传（远程 server）；</li>
 *   <li>{@code stdio}：本地子进程标准输入输出（更高复杂度，非本期范围）。</li>
 * </ul>
 *
 * @author nebula
 */
public interface McpTransport {

    /**
     * 传输类型标识（stdio / sse / streamable-http）。
     *
     * @return 传输类型
     */
    String type();

    /**
     * 发送一个 JSON-RPC 请求并等待响应。
     *
     * @param method MCP 方法名（如 {@code tools/list} / {@code tools/call}）
     * @param params 请求参数
     * @return 响应结果
     */
    Map<String, Object> request(String method, Map<String, Object> params);

    /**
     * 关闭传输通道，释放连接/子进程等资源。
     */
    void close();
}
