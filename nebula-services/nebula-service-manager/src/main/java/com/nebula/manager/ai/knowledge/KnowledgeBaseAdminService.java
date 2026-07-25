package com.nebula.manager.ai.knowledge;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.KnowledgeBasePageQuery;
import com.nebula.manager.dto.KnowledgeBaseSaveRequest;
import com.nebula.manager.dto.KnowledgeDocumentImportRequest;
import com.nebula.manager.vo.KnowledgeBaseVO;
import com.nebula.manager.vo.KnowledgeDocumentVO;
import com.nebula.manager.vo.KnowledgeSearchHitVO;

import java.util.List;

/**
 * 知识库管理服务（管理员端）
 * 承载知识库 CRUD、文档导入/删除、检索预览。CRUD 与文档列表走 Mapper（RAG 关闭亦可读元数据）；导入/检索走
 * {@code KnowledgeService}（需 embedding + 向量库就绪），未就绪时给出明确报错。
 *
 * @author nebula
 */
public interface KnowledgeBaseAdminService {

    /**
     * 分页查询知识库
     */
    PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query);

    /**
     * 知识库详情
     */
    KnowledgeBaseVO detail(Long id);

    /**
     * 创建知识库
     */
    Long create(KnowledgeBaseSaveRequest request);

    /**
     * 更新知识库
     */
    void update(Long id, KnowledgeBaseSaveRequest request);

    /**
     * 删除知识库（含其文档与切片、Milvus 向量）
     */
    void delete(Long id);

    /**
     * 启用/停用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 分页查询知识库下文档
     */
    PageResult<KnowledgeDocumentVO> pageDocuments(String kbCode, KnowledgeBasePageQuery query);

    /**
     * 导入一篇文档（切块 → embed → 入库）
     *
     * @return 切块数
     */
    int importDocument(String kbCode, KnowledgeDocumentImportRequest request);

    /**
     * 删除一篇文档
     */
    void deleteDocument(String kbCode, String docId);

    /**
     * 检索预览（管理员端验证召回效果）
     */
    List<KnowledgeSearchHitVO> search(String kbCode, String query, Integer topK);
}
