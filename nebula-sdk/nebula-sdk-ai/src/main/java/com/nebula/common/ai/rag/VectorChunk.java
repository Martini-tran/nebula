package com.nebula.common.ai.rag;

import java.util.Map;

/**
 * 知识库切片（SPI 占位设计）。
 * 一个文档被切分后的最小检索单元，对应 {@code ai_knowledge_chunk} 表一行。
 *
 * <p><b>本期仅设计，不实现运行时。</b>
 *
 * @param kbCode     所属知识库编码
 * @param docId      来源文档标识
 * @param chunkIndex 切片在文档内的序号
 * @param content    切片正文
 * @param score      检索相似度得分（入库时为 0，检索返回时填充）
 * @param metadata   附加元数据（来源、标题、页码等）
 * @author nebula
 */
public record VectorChunk(
        String kbCode,
        String docId,
        int chunkIndex,
        String content,
        double score,
        Map<String, Object> metadata) {
}
