package com.nebula.common.ai.agent.tool;

/**
 * 工具调用请求（归一化）
 * 模型在一次响应中发起的单个工具调用，由 Provider 从厂商响应里解析归一化而来，屏蔽各厂商原始结构差异。
 * {@code argumentsJson} 保留为原始 JSON 字符串（OpenAI 的 {@code function.arguments} 即字符串），
 * 由 {@code ToolCallingService} 解析为入参 Map 后执行。
 *
 * @param id            工具调用ID，回灌结果时作为 {@code tool_call_id} 与本次调用配对
 * @param name          目标工具名（对应 {@code ToolDefinition.code()}）
 * @param argumentsJson 模型生成的入参 JSON 字符串，可能非法需容错解析
 * @author nebula
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
