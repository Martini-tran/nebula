package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.SeriesAdminPageQuery;
import com.nebula.blog.dto.admin.SeriesCreateRequest;
import com.nebula.blog.dto.admin.SeriesUpdateRequest;
import com.nebula.blog.service.BlogSeriesAdminService;
import com.nebula.blog.vo.admin.SeriesAdminVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 后台系列管理控制器
 */
@RestController
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class SeriesAdminController extends AbstractAdminController {

    private final BlogSeriesAdminService seriesAdminService;

    /**
     * 分页查询系列
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:series:list")
    public R<PageResult<SeriesAdminVO>> page(@ModelAttribute SeriesAdminPageQuery query) {
        return R.success(seriesAdminService.page(query));
    }

    /**
     * 系列详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:series:query")
    public R<SeriesAdminVO> detail(@PathVariable Long id) {
        return R.success(seriesAdminService.detail(id));
    }

    /**
     * 创建系列
     */
    @PostMapping
    @SaCheckPermission("blog:series:add")
    public R<Long> create(@RequestBody @Valid SeriesCreateRequest req) {
        return R.success(seriesAdminService.create(req));
    }

    /**
     * 更新系列
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:series:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid SeriesUpdateRequest req) {
        seriesAdminService.update(id, req);
        return R.success(null);
    }

    /**
     * 删除系列
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:series:delete")
    public R<Void> delete(@PathVariable Long id) {
        seriesAdminService.delete(id);
        return R.success(null);
    }
}
