package com.nebula.common.ai.rag;

import java.util.Map;

/**
 * 泛化向量写入单元（SPI）。
 *
 * <p>{@link VectorChunk} 是知识库场景专用的写入单元（以 kbCode/docId/chunkIndex 三元组定位），而本 record 是四场景通用的
 * 写入单元：把「一条要入库的向量数据」抽象为「确定性主键 + 向量 + 正文 + 标量过滤字段」，由 {@link VectorStore#upsert(String, java.util.List)}
 * 写入指定 collection。适用于 flow_example / memory / tool_catalog 等标量结构各异的场景。
 *
 * <p>{@code pk} 必须是业务确定性键（如 {@code ai_memory.id}、{@code flow_code}），Milvus 主键去重靠它保证 upsert 幂等。
 * {@code scalars} 承载各 collection 专有的标量过滤字段（如 {@code agent_code}/{@code user_id}/{@code item_type}），检索时用于
 * 构造 {@code filterExpr}；{@code metadata} 承载不参与过滤、仅回显的附加信息。
 *
 * @param pk       业务确定性主键，保证 upsert 幂等
 * @param vector   向量（维度须与建库一致）
 * @param content  正文副本（检索直返省回查 MySQL）
 * @param scalars  标量过滤字段（参与 Milvus filterExpr）
 * @param metadata 附加元数据（仅回显，不参与过滤）
 * @author nebula
 */
public record VectorRecord(
        String pk,
        float[] vector,
        String content,
        Map<String, Object> scalars,
        Map<String, Object> metadata) {
}
