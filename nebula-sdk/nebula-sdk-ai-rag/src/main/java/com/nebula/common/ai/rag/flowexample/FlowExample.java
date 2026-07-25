package com.nebula.common.ai.rag.flowexample;

/**
 * Flow few-shot 召回单元（场景②）。
 *
 * <p>{@link FlowExampleService#recall} 的返回单元：一条与查询语义相近的历史流程摘要。由 Copilot 侧（manager）
 * 拼进 system prompt 的「相似流程示例」追加段，引导模型生成结构更贴合既有实践的流程。
 *
 * @param flowCode    流程编码
 * @param name        流程名称
 * @param description 流程描述（正文副本，直取自向量库省回查 DB）
 * @param score       归一化相似度得分（{@code [0,1]}，降序）
 * @author nebula
 */
public record FlowExample(
        String flowCode,
        String name,
        String description,
        double score) {
}
