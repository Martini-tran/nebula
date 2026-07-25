package com.nebula.manager.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.knowledge.KnowledgeService;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeBase;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeBaseMapper;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeChunkMapper;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeChunk;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeDocument;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeDocumentMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.KnowledgeBasePageQuery;
import com.nebula.manager.dto.KnowledgeBaseSaveRequest;
import com.nebula.manager.dto.KnowledgeDocumentImportRequest;
import com.nebula.manager.vo.KnowledgeBaseVO;
import com.nebula.manager.vo.KnowledgeDocumentVO;
import com.nebula.manager.vo.KnowledgeSearchHitVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 知识库管理服务实现（管理员端）
 * CRUD 与文档列表直接操作 {@code ai_knowledge_*} 三表（RAG 关闭亦可读写元数据）；导入/检索委托 {@link KnowledgeService}
 * （需 embedding + 向量库就绪，经 {@link ObjectProvider} 注入，未就绪抛明确 4xx）。删除知识库时级联清其文档/切片与 Milvus 向量。
 *
 * @author nebula
 */
@Service
public class KnowledgeBaseAdminServiceImpl implements KnowledgeBaseAdminService {

    private final AiKnowledgeBaseMapper baseMapper;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final ObjectProvider<KnowledgeService> knowledgeService;

    public KnowledgeBaseAdminServiceImpl(AiKnowledgeBaseMapper baseMapper,
                                         AiKnowledgeDocumentMapper documentMapper,
                                         AiKnowledgeChunkMapper chunkMapper,
                                         ObjectProvider<KnowledgeService> knowledgeService) {
        this.baseMapper = baseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query) {
        KnowledgeBasePageQuery safe = query == null ? new KnowledgeBasePageQuery() : query;
        Page<AiKnowledgeBase> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<AiKnowledgeBase>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiKnowledgeBase::getKbCode, safe.getKeyword())
                        .or()
                        .like(AiKnowledgeBase::getName, safe.getKeyword()))
                .eq(safe.getStatus() != null, AiKnowledgeBase::getStatus, safe.getStatus())
                .orderByDesc(AiKnowledgeBase::getUpdateTime);
        Page<AiKnowledgeBase> result = baseMapper.selectPage(page, wrapper);
        List<KnowledgeBaseVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public KnowledgeBaseVO detail(Long id) {
        return toVO(getExisting(id));
    }

    @Override
    public Long create(KnowledgeBaseSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getKbCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "知识库编码不能为空");
        }
        if (exists(request.getKbCode(), null)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "知识库编码已存在: " + request.getKbCode());
        }
        AiKnowledgeBase entity = new AiKnowledgeBase();
        entity.setKbCode(request.getKbCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setEmbeddingProvider(request.getEmbeddingProvider());
        entity.setEmbeddingModel(request.getEmbeddingModel());
        entity.setDimension(request.getDimension());
        entity.setMetric(request.getMetric());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, KnowledgeBaseSaveRequest request) {
        AiKnowledgeBase entity = getExisting(id);
        // 编码不可变更；维度/模型创建后一般不改（改需重建库），此处仅允许改名/描述/度量/状态
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getMetric())) {
            entity.setMetric(request.getMetric());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        baseMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        AiKnowledgeBase entity = getExisting(id);
        String kbCode = entity.getKbCode();
        // 级联清文档 → 每篇文档删切片 + Milvus 向量
        List<AiKnowledgeDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getKbCode, kbCode));
        KnowledgeService svc = knowledgeService.getIfAvailable();
        for (AiKnowledgeDocument doc : docs) {
            if (svc != null) {
                svc.deleteDocument(kbCode, doc.getDocId());
            } else {
                chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
                        .eq(AiKnowledgeChunk::getKbCode, kbCode)
                        .eq(AiKnowledgeChunk::getDocId, doc.getDocId()));
            }
        }
        documentMapper.delete(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getKbCode, kbCode));
        baseMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值非法");
        }
        AiKnowledgeBase entity = getExisting(id);
        entity.setStatus(status);
        baseMapper.updateById(entity);
    }

    @Override
    public PageResult<KnowledgeDocumentVO> pageDocuments(String kbCode, KnowledgeBasePageQuery query) {
        requireKbExists(kbCode);
        KnowledgeBasePageQuery safe = query == null ? new KnowledgeBasePageQuery() : query;
        Page<AiKnowledgeDocument> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiKnowledgeDocument> wrapper = new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getKbCode, kbCode)
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiKnowledgeDocument::getTitle, safe.getKeyword())
                        .or()
                        .like(AiKnowledgeDocument::getDocId, safe.getKeyword()))
                .orderByDesc(AiKnowledgeDocument::getUpdateTime);
        Page<AiKnowledgeDocument> result = documentMapper.selectPage(page, wrapper);
        List<KnowledgeDocumentVO> rows = result.getRecords().stream().map(this::toDocVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public int importDocument(String kbCode, KnowledgeDocumentImportRequest request) {
        requireKbExists(kbCode);
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "正文不能为空");
        }
        KnowledgeService svc = requireKnowledgeService();
        String docId = StringUtils.hasText(request.getDocId())
                ? request.getDocId() : UUID.randomUUID().toString().replace("-", "");
        String sourceType = StringUtils.hasText(request.getSourceType()) ? request.getSourceType() : "text";
        try {
            return svc.importDocument(kbCode, docId, request.getTitle(), sourceType,
                    request.getSourceUri(), request.getContent());
        } catch (RuntimeException e) {
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "文档导入失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteDocument(String kbCode, String docId) {
        requireKbExists(kbCode);
        KnowledgeService svc = knowledgeService.getIfAvailable();
        if (svc != null) {
            svc.deleteDocument(kbCode, docId);
        } else {
            chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
                    .eq(AiKnowledgeChunk::getKbCode, kbCode)
                    .eq(AiKnowledgeChunk::getDocId, docId));
            documentMapper.delete(new LambdaQueryWrapper<AiKnowledgeDocument>()
                    .eq(AiKnowledgeDocument::getKbCode, kbCode)
                    .eq(AiKnowledgeDocument::getDocId, docId));
        }
    }

    @Override
    public List<KnowledgeSearchHitVO> search(String kbCode, String query, Integer topK) {
        requireKbExists(kbCode);
        if (!StringUtils.hasText(query)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "查询文本不能为空");
        }
        KnowledgeService svc = requireKnowledgeService();
        int k = topK == null || topK <= 0 ? 5 : topK;
        try {
            List<VectorMatch> matches = svc.search(kbCode, query, k, 0);
            return matches.stream().map(m -> {
                KnowledgeSearchHitVO vo = new KnowledgeSearchHitVO();
                vo.setContent(m.content());
                vo.setScore(m.score());
                vo.setMetadata(m.metadata());
                return vo;
            }).toList();
        } catch (RuntimeException e) {
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "检索失败: " + e.getMessage());
        }
    }

    private KnowledgeService requireKnowledgeService() {
        KnowledgeService svc = knowledgeService.getIfAvailable();
        if (svc == null) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE,
                    "知识库 RAG 未启用（需配置 nebula.ai.embedding.* / nebula.ai.milvus.* / nebula.ai.rag.knowledge.enabled=true）");
        }
        return svc;
    }

    private AiKnowledgeBase getExisting(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "知识库ID不能为空");
        }
        AiKnowledgeBase entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "知识库不存在: " + id);
        }
        return entity;
    }

    private void requireKbExists(String kbCode) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getKbCode, kbCode));
        if (count == null || count == 0) {
            throw new BizException(HttpStatus.NOT_FOUND, "知识库不存在: " + kbCode);
        }
    }

    private boolean exists(String kbCode, Long excludeId) {
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getKbCode, kbCode)
                .ne(excludeId != null, AiKnowledgeBase::getId, excludeId);
        return baseMapper.selectCount(wrapper) > 0;
    }

    private KnowledgeBaseVO toVO(AiKnowledgeBase entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(entity.getId());
        vo.setKbCode(entity.getKbCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setEmbeddingProvider(entity.getEmbeddingProvider());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setDimension(entity.getDimension());
        vo.setMetric(entity.getMetric());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private KnowledgeDocumentVO toDocVO(AiKnowledgeDocument entity) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(entity.getId());
        vo.setKbCode(entity.getKbCode());
        vo.setDocId(entity.getDocId());
        vo.setTitle(entity.getTitle());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceUri(entity.getSourceUri());
        vo.setCharCount(entity.getCharCount());
        vo.setChunkCount(entity.getChunkCount());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
