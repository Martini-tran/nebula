package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.TravelCheckinCreateRequest;
import com.nebula.blog.dto.admin.TravelCheckinUpdateRequest;
import com.nebula.blog.service.TravelCheckinAdminService;
import com.nebula.blog.vo.admin.TravelCheckinAdminVO;
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
 * 后台打卡点管理控制器
 */
@RestController
@RequestMapping("/admin/travel/checkins")
@RequiredArgsConstructor
public class TravelCheckinAdminController extends AbstractAdminController {

    private final TravelCheckinAdminService checkinAdminService;

    /**
     * 列出某行程日下的所有打卡点
     */
    @GetMapping
    @SaCheckPermission("blog:travel:list")
    public R<List<TravelCheckinAdminVO>> list(@RequestParam Long tripDayId) {
        return R.success(checkinAdminService.listByTripDay(tripDayId));
    }

    /**
     * 打卡点详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:travel:query")
    public R<TravelCheckinAdminVO> detail(@PathVariable Long id) {
        return R.success(checkinAdminService.detail(id));
    }

    /**
     * 创建打卡点
     */
    @PostMapping
    @SaCheckPermission("blog:travel:add")
    public R<Long> create(@RequestBody @Valid TravelCheckinCreateRequest req) {
        return R.success(checkinAdminService.create(req));
    }

    /**
     * 更新打卡点
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:travel:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid TravelCheckinUpdateRequest req) {
        checkinAdminService.update(id, req);
        return R.success();
    }

    /**
     * 删除打卡点
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:travel:delete")
    public R<Void> delete(@PathVariable Long id) {
        checkinAdminService.delete(id);
        return R.success();
    }
}
