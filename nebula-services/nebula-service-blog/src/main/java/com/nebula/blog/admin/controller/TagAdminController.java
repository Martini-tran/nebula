package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.TagCreateRequest;
import com.nebula.blog.dto.admin.TagUpdateRequest;
import com.nebula.blog.service.BlogTagAdminService;
import com.nebula.blog.vo.admin.TagAdminVO;
import com.nebula.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台标签管理控制器
 */
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class TagAdminController extends AbstractAdminController {

    private final BlogTagAdminService tagAdminService;

    /**
     * 查询标签列表
     */
    @GetMapping
    @SaCheckPermission("blog:tag:list")
    public R<List<TagAdminVO>> list() {
        return R.success(tagAdminService.list());
    }

    /**
     * 查询标签详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:tag:query")
    public R<TagAdminVO> detail(@PathVariable Long id) {
        return R.success(tagAdminService.detail(id));
    }

    /**
     * 创建标签
     */
    @PostMapping
    @SaCheckPermission("blog:tag:add")
    public R<Long> create(@RequestBody @Valid TagCreateRequest req) {
        return R.success(tagAdminService.create(req));
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:tag:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid TagUpdateRequest req) {
        tagAdminService.update(id, req);
        return R.success();
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:tag:delete")
    public R<Void> delete(@PathVariable Long id) {
        tagAdminService.delete(id);
        return R.success();
    }
}
