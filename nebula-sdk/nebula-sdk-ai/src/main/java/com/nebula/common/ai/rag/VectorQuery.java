package com.nebula.common.ai.rag;

/**
 * 泛化向量检索请求（SPI）。
 *
 * <p>把一次相似度检索的全部参数打包：目标 collection、租户分区、查询向量、topK、标量过滤表达式与最小得分阈值。
 * 由 {@link VectorStore#search(VectorQuery)} 消费，适配四场景各异的过滤需求。
 *
 * <p>{@code partition} 对应 collection 的 partitionKey（如 {@code kbCode}/{@code agentCode}/{@code owner}），走 Milvus 分区裁剪
 * 实现多租户物理隔离 + 提速，可空（不按分区裁剪）。{@code filterExpr} 是 Milvus 布尔表达式（如
 * {@code user_id == "u1" && mem_type == "fact"}），用于场景③④的标量过滤，可空。{@code minScore} 按归一化得分
 * （{@code [0,1]}）过滤，{@code <=0} 表示不过滤。
 *
 * @param collection 目标 collection 逻辑名（实现按前缀/维度/alias 解析物理名）
 * @param partition  租户分区键值（对应 partitionKey），可空
 * @param vector     查询向量（维度须与建库一致）
 * @param topK       返回条数
 * @param filterExpr Milvus 标量过滤表达式，可空
 * @param minScore   最小归一化得分阈值（{@code [0,1]}），{@code <=0} 不过滤
 * @author nebula
 */
public record VectorQuery(
        String collection,
        String partition,
        float[] vector,
        int topK,
        String filterExpr,
        double minScore) {
}
