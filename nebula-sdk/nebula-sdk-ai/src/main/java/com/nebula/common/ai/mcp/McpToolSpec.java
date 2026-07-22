package com.nebula.common.ai.mcp;

import java.util.Map;

/**
 * MCP 工具规格（SPI 占位设计）。
 * 一个 MCP server 暴露的单个工具的自描述元数据，对应 MCP 协议 {@code tools/list} 返回的一项。
 *
 * <p><b>本期仅设计，不实现运行时。</b>
 *
 * @param name        工具名（MCP server 内唯一）
 * @param description 工具描述
 * @param inputSchema 入参 JSON Schema（可直接作为 ToolDefinition.paramsSchema）
 * @author nebula
 */
public record McpToolSpec(String name, String description, Map<String, Object> inputSchema) {
}
