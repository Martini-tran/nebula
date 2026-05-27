package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.TravelTripDayCreateRequest;
import com.nebula.blog.dto.admin.TravelTripDayUpdateRequest;
import com.nebula.blog.service.TravelTripDayAdminService;
import com.nebula.blog.vo.admin.TravelTripDayAdminVO;
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
 * 后台行程日管理控制器
 */
@RestController
@RequestMapping("/admin/travel/trip-days")
@RequiredArgsConstructor
public class TravelTripDayAdminController extends AbstractAdminController {

    private final TravelTripDayAdminService tripDayAdminService;

    /**
     * 列出某游记下的所有行程日
     */
    @GetMapping
    @SaCheckPermission("blog:travel:list")
    public R<List<TravelTripDayAdminVO>> list(@RequestParam Long tripId) {
        return R.success(tripDayAdminService.listByTrip(tripId));
    }

    /**
     * 行程日详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:travel:query")
    public R<TravelTripDayAdminVO> detail(@PathVariable Long id) {
        return R.success(tripDayAdminService.detail(id));
    }

    /**
     * 创建行程日
     */
    @PostMapping
    @SaCheckPermission("blog:travel:add")
    public R<Long> create(@RequestBody @Valid TravelTripDayCreateRequest req) {
        return R.success(tripDayAdminService.create(req));
    }

    /**
     * 更新行程日
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:travel:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid TravelTripDayUpdateRequest req) {
        tripDayAdminService.update(id, req);
        return R.success();
    }

    /**
     * 删除行程日（级联清理打卡点）
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:travel:delete")
    public R<Void> delete(@PathVariable Long id) {
        tripDayAdminService.delete(id);
        return R.success();
    }
}
