package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.PostImportTaskPageQuery;
import com.nebula.blog.service.BlogPostImportTaskService;
import com.nebula.blog.vo.admin.PostImportTaskVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台文章导入任务控制器。
 * <p>配合 {@code POST /admin/articles/import}（异步导入返回任务 ID）使用，提供任务进度/明细查询。
 */
@RestController
@RequestMapping("/admin/articles/import-tasks")
@RequiredArgsConstructor
public class PostImportTaskAdminController extends AbstractAdminController {

    private final BlogPostImportTaskService importTaskService;

    /**
     * 分页查询导入任务（不含逐文件明细）
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:article:list")
    public R<PageResult<PostImportTaskVO>> page(@ModelAttribute PostImportTaskPageQuery query) {
        return R.success(importTaskService.page(query));
    }

    /**
     * 查询导入任务详情（含逐文件明细），前端轮询此接口观测进度
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:article:query")
    public R<PostImportTaskVO> detail(@PathVariable Long id) {
        return R.success(importTaskService.detail(id));
    }
}
