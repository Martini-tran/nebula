package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.SeriesCatalogCreateRequest;
import com.nebula.blog.dto.admin.SeriesCatalogPostBindRequest;
import com.nebula.blog.dto.admin.SeriesCatalogUpdateRequest;
import com.nebula.blog.service.BlogSeriesCatalogAdminService;
import com.nebula.blog.vo.admin.SeriesCatalogAdminVO;
import com.nebula.blog.vo.admin.SeriesCatalogPostVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台系列目录管理控制器
 */
@RestController
@RequestMapping("/admin/series/catalogs")
@RequiredArgsConstructor
public class SeriesCatalogAdminController extends AbstractAdminController {

    private final BlogSeriesCatalogAdminService catalogService;

    /**
     * 获取指定系列的目录树
     */
    @GetMapping("/tree")
    @SaCheckPermission("blog:series:list")
    public R<List<SeriesCatalogAdminVO>> tree(@RequestParam Long seriesId) {
        return R.success(catalogService.getCatalogTree(seriesId));
    }

    /**
     * 创建目录节点
     */
    @PostMapping
    @SaCheckPermission("blog:series:edit")
    public R<Long> create(@RequestBody @Valid SeriesCatalogCreateRequest req) {
        return R.success(catalogService.create(req));
    }

    /**
     * 更新目录节点
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:series:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid SeriesCatalogUpdateRequest req) {
        catalogService.update(id, req);
        return R.success();
    }

    /**
     * 删除目录节点（含其下所有子节点）
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:series:edit")
    public R<Void> delete(@PathVariable Long id) {
        catalogService.delete(id);
        return R.success();
    }

    /**
     * 查询目录节点下的文章列表
     */
    @GetMapping("/{id}/posts")
    @SaCheckPermission("blog:series:query")
    public R<List<SeriesCatalogPostVO>> listPosts(@PathVariable Long id) {
        return R.success(catalogService.listPosts(id));
    }

    /**
     * 全量替换目录节点的文章绑定
     */
    @PutMapping("/{id}/posts")
    @SaCheckPermission("blog:series:edit")
    public R<Void> bindPosts(@PathVariable Long id, @RequestBody @Valid SeriesCatalogPostBindRequest req) {
        catalogService.bindPosts(id, req);
        return R.success();
    }
}
