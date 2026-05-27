package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.TravelTripAdminPageQuery;
import com.nebula.blog.dto.admin.TravelTripCreateRequest;
import com.nebula.blog.dto.admin.TravelTripPostBindRequest;
import com.nebula.blog.dto.admin.TravelTripStatusUpdateRequest;
import com.nebula.blog.dto.admin.TravelTripUpdateRequest;
import com.nebula.blog.service.TravelTripAdminService;
import com.nebula.blog.vo.admin.TravelTripAdminVO;
import com.nebula.blog.vo.admin.TravelTripPostVO;
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

import java.util.List;

/**
 * 后台游记管理控制器
 */
@RestController
@RequestMapping("/admin/travel/trips")
@RequiredArgsConstructor
public class TravelTripAdminController extends AbstractAdminController {

    private final TravelTripAdminService tripAdminService;

    /**
     * 分页查询游记
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:travel:list")
    public R<PageResult<TravelTripAdminVO>> page(@ModelAttribute TravelTripAdminPageQuery query) {
        return R.success(tripAdminService.page(query));
    }

    /**
     * 游记详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:travel:query")
    public R<TravelTripAdminVO> detail(@PathVariable Long id) {
        return R.success(tripAdminService.detail(id));
    }

    /**
     * 创建游记
     */
    @PostMapping
    @SaCheckPermission("blog:travel:add")
    public R<Long> create(@RequestBody @Valid TravelTripCreateRequest req) {
        return R.success(tripAdminService.create(req));
    }

    /**
     * 更新游记
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:travel:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid TravelTripUpdateRequest req) {
        tripAdminService.update(id, req);
        return R.success();
    }

    /**
     * 更新游记状态/可见性
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:travel:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid TravelTripStatusUpdateRequest req) {
        tripAdminService.updateStatus(id, req);
        return R.success();
    }

    /**
     * 删除游记（级联清理 trip_day / checkin / trip_blog_post）
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:travel:delete")
    public R<Void> delete(@PathVariable Long id) {
        tripAdminService.delete(id);
        return R.success();
    }

    /**
     * 查询游记关联的博客文章
     */
    @GetMapping("/{id}/posts")
    @SaCheckPermission("blog:travel:query")
    public R<List<TravelTripPostVO>> listPosts(@PathVariable Long id) {
        return R.success(tripAdminService.listPosts(id));
    }

    /**
     * 全量替换游记的博客文章关联
     */
    @PutMapping("/{id}/posts")
    @SaCheckPermission("blog:travel:edit")
    public R<Void> bindPosts(@PathVariable Long id, @RequestBody @Valid TravelTripPostBindRequest req) {
        tripAdminService.bindPosts(id, req);
        return R.success();
    }
}
