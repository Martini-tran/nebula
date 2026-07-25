package com.nebula.manager.ai.knowledge;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.KnowledgeBasePageQuery;
import com.nebula.manager.dto.KnowledgeBaseSaveRequest;
import com.nebula.manager.dto.KnowledgeDocumentImportRequest;
import com.nebula.manager.vo.KnowledgeBaseVO;
import com.nebula.manager.vo.KnowledgeDocumentVO;
import com.nebula.manager.vo.KnowledgeSearchHitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI知识库管理控制器（管理员端）
 * 维护 {@code ai_knowledge_*}：知识库 CRUD/启停 + 文档导入/列表/删除 + 检索预览。导入/检索依赖 embedding + 向量库就绪，
 * 未就绪时返回明确错误。照 {@code ModelProfileAdmin} 模板（camelCase 出参），权限 {@code manager:ai-knowledge:*}。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseAdminController {

    private final KnowledgeBaseAdminService knowledgeBaseAdminService;

    // —— 知识库 CRUD ——

    /**
     * 分页查询知识库
     */
    @GetMapping({"/bases", "/bases/page"})
    @SaCheckPermission("manager:ai-knowledge:list")
    public R<PageResult<KnowledgeBaseVO>> page(@ModelAttribute KnowledgeBasePageQuery query) {
        return R.success(knowledgeBaseAdminService.page(query));
    }

    /**
     * 知识库详情
     */
    @GetMapping("/bases/{id}")
    @SaCheckPermission("manager:ai-knowledge:query")
    public R<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return R.success(knowledgeBaseAdminService.detail(id));
    }

    /**
     * 创建知识库
     */
    @PostMapping("/bases")
    @SaCheckPermission("manager:ai-knowledge:add")
    public R<Long> create(@RequestBody KnowledgeBaseSaveRequest request) {
        return R.success("create success", knowledgeBaseAdminService.create(request));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/bases/{id}")
    @SaCheckPermission("manager:ai-knowledge:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody KnowledgeBaseSaveRequest request) {
        knowledgeBaseAdminService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 删除知识库（含文档/切片/向量）
     */
    @DeleteMapping("/bases/{id}")
    @SaCheckPermission("manager:ai-knowledge:delete")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseAdminService.delete(id);
        return R.success("delete success", null);
    }

    /**
     * 启用/停用
     */
    @PutMapping("/bases/{id}/status")
    @SaCheckPermission("manager:ai-knowledge:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        knowledgeBaseAdminService.updateStatus(id, status);
        return R.success("status updated", null);
    }

    // —— 文档导入/列表/删除 ——

    /**
     * 分页查询知识库下文档
     */
    @GetMapping("/bases/{kbCode}/documents")
    @SaCheckPermission("manager:ai-knowledge:query")
    public R<PageResult<KnowledgeDocumentVO>> pageDocuments(@PathVariable String kbCode,
                                                            @ModelAttribute KnowledgeBasePageQuery query) {
        return R.success(knowledgeBaseAdminService.pageDocuments(kbCode, query));
    }

    /**
     * 导入一篇文档（切块 → embed → 入库）
     */
    @PostMapping("/bases/{kbCode}/documents")
    @SaCheckPermission("manager:ai-knowledge:import")
    public R<Integer> importDocument(@PathVariable String kbCode,
                                     @RequestBody KnowledgeDocumentImportRequest request) {
        return R.success("import success", knowledgeBaseAdminService.importDocument(kbCode, request));
    }

    /**
     * 删除一篇文档
     */
    @DeleteMapping("/bases/{kbCode}/documents/{docId}")
    @SaCheckPermission("manager:ai-knowledge:import")
    public R<Void> deleteDocument(@PathVariable String kbCode, @PathVariable String docId) {
        knowledgeBaseAdminService.deleteDocument(kbCode, docId);
        return R.success("delete success", null);
    }

    // —— 检索预览 ——

    /**
     * 检索预览（验证召回效果）
     */
    @GetMapping("/bases/{kbCode}/search")
    @SaCheckPermission("manager:ai-knowledge:query")
    public R<List<KnowledgeSearchHitVO>> search(@PathVariable String kbCode,
                                                @RequestParam String query,
                                                @RequestParam(required = false) Integer topK) {
        return R.success(knowledgeBaseAdminService.search(kbCode, query, topK));
    }
}
