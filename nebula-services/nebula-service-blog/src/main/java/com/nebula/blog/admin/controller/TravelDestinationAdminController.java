package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.TravelDestinationCreateRequest;
import com.nebula.blog.dto.admin.TravelDestinationUpdateRequest;
import com.nebula.blog.service.TravelDestinationAdminService;
import com.nebula.blog.vo.admin.TravelDestinationAdminVO;
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
 * 后台旅游目的地管理控制器
 */
@RestController
@RequestMapping("/admin/travel/destinations")
@RequiredArgsConstructor
public class TravelDestinationAdminController extends AbstractAdminController {

    private final TravelDestinationAdminService destinationAdminService;

    /**
     * 获取目的地树
     */
    @GetMapping("/tree")
    @SaCheckPermission("blog:travel:list")
    public R<List<TravelDestinationAdminVO>> tree() {
        return R.success(destinationAdminService.tree());
    }

    /**
     * 目的地详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:travel:query")
    public R<TravelDestinationAdminVO> detail(@PathVariable Long id) {
        return R.success(destinationAdminService.detail(id));
    }

    /**
     * 创建目的地
     */
    @PostMapping
    @SaCheckPermission("blog:travel:add")
    public R<Long> create(@RequestBody @Valid TravelDestinationCreateRequest req) {
        return R.success(destinationAdminService.create(req));
    }

    /**
     * 更新目的地
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:travel:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid TravelDestinationUpdateRequest req) {
        destinationAdminService.update(id, req);
        return R.success();
    }

    /**
     * 删除目的地
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:travel:delete")
    public R<Void> delete(@PathVariable Long id) {
        destinationAdminService.delete(id);
        return R.success();
    }
}
