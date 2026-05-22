package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.dto.admin.PostAdminPageQuery;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostStatusUpdateRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.vo.admin.PostAdminVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.nebula.blog.controller.AbstractAdminController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台文章管理控制器
 */
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class PostAdminController extends AbstractAdminController {

    private final BlogPostAdminService postAdminService;

    /**
     * 分页查询文章
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:post:list")
    public R<PageResult<PostAdminVO>> page(@ModelAttribute PostAdminPageQuery query) {
        return R.success(postAdminService.page(query));
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:post:query")
    public R<PostAdminVO> detail(@PathVariable Long id) {
        return R.success(postAdminService.detail(id));
    }

    /**
     * 创建文章
     */
    @PostMapping
    @SaCheckPermission("blog:post:add")
    public R<Long> create(@RequestBody @Valid PostCreateRequest req) {
        return R.success(postAdminService.create(req));
    }

    /**
     * 更新文章
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:post:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid PostUpdateRequest req) {
        postAdminService.update(id, req);
        return R.success(null);
    }

    /**
     * 更新文章状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:post:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid PostStatusUpdateRequest req) {
        postAdminService.updateStatus(id, req);
        return R.success(null);
    }

    /**
     * 删除文章
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:post:delete")
    public R<Void> delete(@PathVariable Long id) {
        postAdminService.delete(id);
        return R.success(null);
    }
}
