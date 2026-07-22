package com.nebula.common.ai.rag;

import java.util.List;

/**
 * 文本向量化提供者（SPI，占位设计）。
 *
 * <p><b>本期仅设计，不实现运行时。</b>把「一段文本 → 向量」的能力抽成 SPI，将来可由 OpenAI 兼容的
 * {@code /embeddings} 端点、本地模型或第三方服务实现。实现类注册为 Bean 后，供知识库入库（切分后逐块 embed）
 * 与检索（query embed）复用。
 *
 * <p>接入点：与业务工具共用同一 {@code ToolRegistry} seam —— 检索能力包成一个
 * {@code KnowledgeSearchToolDefinition implements ToolDefinition}（code=knowledge_search，
 * params={kbCode, query, topK}，invoke = embed(query) → {@link VectorStore#search} → 返回 top-k），
 * 注册为 {@code @Component} 即可被流程 TOOL 节点或流程设计助手使用，无需改动编排内核。
 *
 * @author nebula
 */
public interface EmbeddingProvider {

    /**
     * 提供者编码，全局唯一（与 {@code ai_knowledge_base.embedding_provider} 对应）。
     *
     * @return 提供者编码
     */
    String code();

    /**
     * 批量把文本向量化。返回列表与入参一一对应。
     *
     * @param texts 待向量化文本
     * @return 向量列表（每个为定长 float 数组，维度 = {@link #dimension()}）
     */
    List<float[]> embed(List<String> texts);

    /**
     * 向量维度（须与知识库建库时声明的维度一致，否则检索无意义）。
     *
     * @return 维度
     */
    int dimension();
}
