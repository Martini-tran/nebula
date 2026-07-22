package com.nebula.common.ai.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 客户端（SPI，占位设计）。
 *
 * <p><b>本期仅设计，不实现运行时。</b>面向一个已登记的 MCP server（{@code ai_mcp_server}），
 * 通过 {@link McpTransport} 列出并调用其工具。
 *
 * <p>接入点：把每个 MCP 工具包成一个 {@code McpToolAdapter implements ToolDefinition}
 * （code={@code mcp:<serverCode>:<toolName>}，paramsSchema 取 {@link McpToolSpec#inputSchema()}，
 * invoke 转发 {@link #callTool}），启动时按已登记 server 动态注册这些适配器 Bean →
 * 经 {@code ToolRegistrySynchronizer} 自动进 {@code ai_tool} 表 → 流程 TOOL 节点 / 流程设计助手可用。
 * 这样 MCP 工具与代码内工具、知识库检索工具走的是同一套注册与调用机制。
 *
 * @author nebula
 */
public interface McpClient {

    /**
     * 所属 MCP server 编码（与 {@code ai_mcp_server.server_code} 对应）。
     *
     * @return server 编码
     */
    String serverCode();

    /**
     * 列出该 server 暴露的全部工具。
     *
     * @return 工具规格列表
     */
    List<McpToolSpec> listTools();

    /**
     * 调用该 server 的一个工具。
     *
     * @param toolName 工具名
     * @param args     入参
     * @return 工具产物
     */
    Object callTool(String toolName, Map<String, Object> args);
}
