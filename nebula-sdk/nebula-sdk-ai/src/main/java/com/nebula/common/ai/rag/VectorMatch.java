package com.nebula.common.ai.rag;

import java.util.Map;

/**
 * 泛化向量检索命中（SPI）。
 *
 * <p>{@link VectorStore#search(VectorQuery)} 的返回单元，与 {@link VectorRecord} 对称：把「一条检索命中」抽象为
 * 「主键 + 正文 + 相似度得分 + 标量 + 元数据」。{@code score} 已由实现统一归一化到 {@code [0,1]}（COSINE 距离转
 * {@code (cos+1)/2}），降序返回，可直接与 {@link VectorQuery#minScore()} 比较。
 *
 * @param pk       命中记录的业务主键
 * @param content  正文副本
 * @param score    归一化相似度得分（{@code [0,1]}，降序）
 * @param scalars  标量字段
 * @param metadata 附加元数据
 * @author nebula
 */
public record VectorMatch(
        String pk,
        String content,
        double score,
        Map<String, Object> scalars,
        Map<String, Object> metadata) {
}
