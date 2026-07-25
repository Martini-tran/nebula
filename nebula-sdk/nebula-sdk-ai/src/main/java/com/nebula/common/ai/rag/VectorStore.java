package com.nebula.common.ai.rag;

import java.util.List;

/**
 * 向量存储与检索（SPI）。
 *
 * <p>抽象「向量的写入与相似度检索」，与具体后端解耦：
 * <ul>
 *   <li>最简实现：向量列用 JSON/BLOB 存 {@code float[]}，检索走应用层余弦（数据量小时可用）；</li>
 *   <li>进阶实现：接 pgvector / Milvus / Qdrant 等专用向量库（在本 SPI 之下适配，见 {@code nebula-sdk-ai-rag}）。</li>
 * </ul>
 *
 * <p>本接口提供两组方法：
 * <ol>
 *   <li><b>知识库门面</b>（{@link #upsert(List, List)} / {@link #search(String, float[], int)} /
 *       {@link #deleteByDoc(String, String)}）：以 {@code kbCode} 为唯一命名空间，服务场景① 知识库 RAG，向后兼容；
 *       实现内部委托到泛化方法（collection={@code nebula_kb}，partition={@code kbCode}）。</li>
 *   <li><b>泛化方法</b>（{@link #upsert(String, List)} / {@link #search(VectorQuery)} / {@link #delete(String, String)}）：
 *       以任意 collection + 灵活标量过滤为参数，服务场景② Flow Copilot few-shot、③ 长期记忆语义召回、④ 节点/工具语义检索。</li>
 * </ol>
 *
 * <p><b>score 归一化</b>：实现须把 COSINE 距离统一转 {@code [0,1]}（{@code (cos+1)/2}）后回填 {@link VectorMatch#score()} /
 * {@link VectorChunk#score()}，降序返回；{@link VectorQuery#minScore()} 按归一化值过滤。
 *
 * @author nebula
 */
public interface VectorStore {

    // —— 知识库门面（向后兼容，场景① 用；实现委托到泛化方法）——

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
     * @return 命中的切片（含归一化相似度得分，降序）
     */
    List<VectorChunk> search(String kbCode, float[] queryVector, int topK);

    /**
     * 删除一个文档在知识库中的全部切片。
     *
     * @param kbCode 知识库编码
     * @param docId  文档标识
     */
    void deleteByDoc(String kbCode, String docId);

    // —— 泛化方法（场景②③④ 用）——

    /**
     * 向指定 collection 写入/更新一批向量记录（按 {@link VectorRecord#pk()} 幂等）。
     *
     * @param collection 目标 collection 逻辑名
     * @param records    向量记录（自带 pk / vector / content / scalars）
     */
    void upsert(String collection, List<VectorRecord> records);

    /**
     * 按 {@link VectorQuery} 做 top-k 相似度检索（支持分区裁剪 + 标量过滤 + 最小得分阈值）。
     *
     * @param query 检索请求
     * @return 命中记录（含归一化得分，降序，已按 minScore 过滤）
     */
    List<VectorMatch> search(VectorQuery query);

    /**
     * 按标量过滤表达式删除指定 collection 中的记录。
     *
     * @param collection 目标 collection 逻辑名
     * @param filterExpr Milvus 布尔过滤表达式（如 {@code pk in [...]} 或 {@code doc_id == "d1"}）
     */
    void delete(String collection, String filterExpr);
}
