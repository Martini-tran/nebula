package com.nebula.common.ai.rag.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorChunk;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeBase;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeBaseMapper;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeChunk;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeChunkMapper;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeDocument;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeDocumentMapper;
import com.nebula.common.ai.rag.milvus.MilvusCollections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务
 * 承载场景① 的全部业务：知识库 CRUD、文档导入流水线（切块 → embed → {@link VectorStore#upsert} + 落
 * {@code ai_knowledge_chunk}）、语义检索（embed(query) → {@link VectorStore#search}）。正文以 {@code ai_knowledge_chunk}
 * 为真相源，向量存 Milvus。
 *
 * <p><b>维度校验</b>：建库落 {@code dimension}，导入/检索前校验 {@link EmbeddingProvider#dimension()} 与建库维度一致，
 * 不一致直接拒绝（防模型漂移脏写）。
 *
 * @author nebula
 */
@Slf4j
public class KnowledgeService {

    private final AiKnowledgeBaseMapper baseMapper;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final AiProperties.Embedding embeddingConfig;

    /**
     * 切块窗口大小（字符）
     */
    private final int chunkWindow;

    /**
     * 切块重叠（字符）
     */
    private final int chunkOverlap;

    public KnowledgeService(AiKnowledgeBaseMapper baseMapper,
                            AiKnowledgeDocumentMapper documentMapper,
                            AiKnowledgeChunkMapper chunkMapper,
                            EmbeddingProvider embeddingProvider,
                            VectorStore vectorStore,
                            AiProperties.Embedding embeddingConfig,
                            int chunkWindow,
                            int chunkOverlap) {
        this.baseMapper = baseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.embeddingConfig = embeddingConfig;
        this.chunkWindow = chunkWindow;
        this.chunkOverlap = chunkOverlap;
    }

    // —— 知识库 CRUD ——

    /**
     * 创建知识库。维度/模型/度量缺省从 embedding 配置补齐。
     *
     * @param kb 知识库（至少含 kbCode/name）
     * @return 主键
     */
    public Long createBase(AiKnowledgeBase kb) {
        if (kb.getEmbeddingProvider() == null) {
            kb.setEmbeddingProvider(embeddingProvider.code());
        }
        if (kb.getEmbeddingModel() == null) {
            kb.setEmbeddingModel(embeddingConfig.getModel());
        }
        if (kb.getDimension() == null) {
            kb.setDimension(embeddingProvider.dimension());
        }
        if (kb.getMetric() == null) {
            kb.setMetric("COSINE");
        }
        if (kb.getStatus() == null) {
            kb.setStatus(1);
        }
        baseMapper.insert(kb);
        return kb.getId();
    }

    /**
     * 按编码取知识库。
     *
     * @param kbCode 编码
     * @return 知识库，不存在返回 null
     */
    public AiKnowledgeBase getBase(String kbCode) {
        return baseMapper.selectOne(new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getKbCode, kbCode)
                .last("limit 1"));
    }

    // —— 文档导入 ——

    /**
     * 导入一篇文档：切块 → embed → upsert Milvus + 落 chunk 表，更新文档状态。
     * 幂等：同 docId 重复导入先删旧切片（DB + Milvus）再写新。
     *
     * @param kbCode     知识库编码
     * @param docId      文档标识
     * @param title      标题
     * @param sourceType 来源类型
     * @param sourceUri  来源地址（可空）
     * @param content    正文
     * @return 切块数
     */
    @Transactional(rollbackFor = Exception.class)
    public int importDocument(String kbCode, String docId, String title,
                              String sourceType, String sourceUri, String content) {
        AiKnowledgeBase kb = requireBase(kbCode);
        assertDimension(kb);

        // 幂等：清旧
        deleteDocumentInternal(kbCode, docId);

        List<String> pieces = TextChunker.chunk(content, chunkWindow, chunkOverlap);
        AiKnowledgeDocument doc = new AiKnowledgeDocument();
        doc.setKbCode(kbCode);
        doc.setDocId(docId);
        doc.setTitle(title);
        doc.setSourceType(sourceType);
        doc.setSourceUri(sourceUri);
        doc.setCharCount(content == null ? 0 : content.length());
        doc.setChunkCount(pieces.size());
        doc.setStatus(AiKnowledgeDocument.STATUS_INDEXING);
        documentMapper.insert(doc);

        try {
            if (!pieces.isEmpty()) {
                List<float[]> embeddings = embeddingProvider.embed(pieces);
                List<VectorChunk> chunks = new ArrayList<>(pieces.size());
                List<AiKnowledgeChunk> chunkRows = new ArrayList<>(pieces.size());
                for (int i = 0; i < pieces.size(); i++) {
                    String piece = pieces.get(i);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("title", title);
                    metadata.put("docId", docId);
                    chunks.add(new VectorChunk(kbCode, docId, i, piece, 0, metadata));

                    AiKnowledgeChunk row = new AiKnowledgeChunk();
                    row.setKbCode(kbCode);
                    row.setDocId(docId);
                    row.setChunkIndex(i);
                    row.setContent(piece);
                    row.setTokenCount(estimateTokens(piece));
                    chunkRows.add(row);
                }
                vectorStore.upsert(chunks, embeddings);
                chunkRows.forEach(chunkMapper::insert);
            }
            doc.setStatus(AiKnowledgeDocument.STATUS_DONE);
            documentMapper.updateById(doc);
            return pieces.size();
        } catch (RuntimeException e) {
            doc.setStatus(AiKnowledgeDocument.STATUS_FAILED);
            documentMapper.updateById(doc);
            throw e;
        }
    }

    /**
     * 删除一篇文档（DB 切片/文档行 + Milvus 向量）。
     *
     * @param kbCode 知识库编码
     * @param docId  文档标识
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String kbCode, String docId) {
        deleteDocumentInternal(kbCode, docId);
    }

    private void deleteDocumentInternal(String kbCode, String docId) {
        chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getKbCode, kbCode)
                .eq(AiKnowledgeChunk::getDocId, docId));
        documentMapper.delete(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getKbCode, kbCode)
                .eq(AiKnowledgeDocument::getDocId, docId));
        vectorStore.deleteByDoc(kbCode, docId);
    }

    // —— 检索 ——

    /**
     * 语义检索：embed(query) → 在指定库内 top-k 相似度检索。
     *
     * @param kbCode   知识库编码
     * @param query    查询文本
     * @param topK     返回条数
     * @param minScore 最小归一化得分阈值（&lt;=0 不过滤）
     * @return 命中切片（含归一化得分，降序）
     */
    public List<VectorMatch> search(String kbCode, String query, int topK, double minScore) {
        AiKnowledgeBase kb = requireBase(kbCode);
        assertDimension(kb);
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<float[]> vectors = embeddingProvider.embed(List.of(query));
        if (vectors.isEmpty()) {
            return List.of();
        }
        VectorQuery vq = new VectorQuery(MilvusCollections.KB, kbCode, vectors.get(0), topK, null, minScore);
        return vectorStore.search(vq);
    }

    private AiKnowledgeBase requireBase(String kbCode) {
        AiKnowledgeBase kb = getBase(kbCode);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + kbCode);
        }
        return kb;
    }

    /**
     * 维度校验：建库维度须与当前 embedding 维度一致，否则拒绝（防脏写）。
     */
    private void assertDimension(AiKnowledgeBase kb) {
        if (kb.getDimension() != null && kb.getDimension() != embeddingProvider.dimension()) {
            throw new IllegalStateException("知识库 " + kb.getKbCode() + " 维度 " + kb.getDimension()
                    + " 与当前 embedding 维度 " + embeddingProvider.dimension() + " 不一致，拒绝读写");
        }
    }

    /**
     * 粗略估算 token 数（按字符数 / 4，中文偏保守，仅用于观测）。
     */
    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }
}
