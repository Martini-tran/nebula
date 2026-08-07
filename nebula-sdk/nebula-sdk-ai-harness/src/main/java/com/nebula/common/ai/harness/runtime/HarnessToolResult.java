package com.nebula.common.ai.harness.runtime;

/**
 * 工具调度结果。
 *
 * @param callId    工具调用ID
 * @param toolCode  工具编码
 * @param success   调用是否成功
 * @param result    原始结构化结果
 * @param content   回灌模型的有界文本
 * @param latencyMs 执行耗时
 * @author nebula
 */
public record HarnessToolResult(
        String callId,
        String toolCode,
        boolean success,
        Object result,
        String content,
        long latencyMs) {
}
