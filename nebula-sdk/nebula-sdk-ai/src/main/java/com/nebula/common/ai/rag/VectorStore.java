package com.nebula.common.ai.rag;

import java.util.List;

/**
 * 向量存储与检索（SPI，占位设计）。
 *
 * <p><b>本期仅设计，不实现运行时。</b>抽象「向量的写入与相似度检索」，与具体后端解耦：
 * <ul>
 *   <li>最简实现：向量列用 JSON/BLOB 存 {@code float[]}，检索走应用层余弦（数据量小时可用）；</li>
 *   <li>进阶实现：接 pgvector / Milvus / Qdrant 等专用向量库（在本 SPI 之下适配）。</li>
 * </ul>
 *
 * @author nebula
 */
public interface VectorStore {

    /**
     * 写入/更新一批切片及其向量（按 kbCode + docId + chunkIndex 幂等）。
     *
     * @param chunks     切片
     * @param embeddings 与切片一一对应的向量
     */
    void upsert(List<VectorChunk> chunks, List<float[]> embeddings);

    /**
     * 在指定知识库内按查询向量做 top-k 相似度检索。
     *
     * @param kbCode      知识库编码
     * @param queryVector 查询向量（维度须与建库一致）
     * @param topK        返回条数
     * @return 命中的切片（含相似度得分，降序）
     */
    List<VectorChunk> search(String kbCode, float[] queryVector, int topK);

    /**
     * 删除一个文档在知识库中的全部切片。
     *
     * @param kbCode 知识库编码
     * @param docId  文档标识
     */
    void deleteByDoc(String kbCode, String docId);
}
