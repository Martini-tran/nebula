package com.nebula.common.ai.harness.runtime;

/**
 * 模型返回的单条工具调用。
 *
 * @param id            调用ID
 * @param name          工具编码
 * @param argumentsJson 原始 JSON 入参
 * @author nebula
 */
public record HarnessToolCall(String id, String name, String argumentsJson) {
}
