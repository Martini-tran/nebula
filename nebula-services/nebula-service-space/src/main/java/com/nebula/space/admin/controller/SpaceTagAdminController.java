package com.nebula.space.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.R;
import com.nebula.space.controller.AbstractAdminController;
import com.nebula.space.dto.admin.SpaceTagCreateRequest;
import com.nebula.space.dto.admin.SpaceTagUpdateRequest;
import com.nebula.space.service.SpaceTagAdminService;
import com.nebula.space.vo.admin.SpaceTagAdminVO;
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
 * 后台空间标签管理控制器
 */
@RestController
@RequestMapping("/admin/space-tags")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceTagAdminController extends AbstractAdminController {

    private final SpaceTagAdminService tagAdminService;

    /**
     * 查询标签列表
     */
    @GetMapping
    @SaCheckPermission("space:tag:list")
    public R<List<SpaceTagAdminVO>> list() {
        return R.success(tagAdminService.list());
    }

    /**
     * 查询标签详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("space:tag:query")
    public R<SpaceTagAdminVO> detail(@PathVariable Long id) {
        return R.success(tagAdminService.detail(id));
    }

    /**
     * 创建标签
     */
    @PostMapping
    @SaCheckPermission("space:tag:add")
    public R<Long> create(@RequestBody @Valid SpaceTagCreateRequest req) {
        return R.success(tagAdminService.create(req));
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    @SaCheckPermission("space:tag:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid SpaceTagUpdateRequest req) {
        tagAdminService.update(id, req);
        return R.success();
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("space:tag:delete")
    public R<Void> delete(@PathVariable Long id) {
        tagAdminService.delete(id);
        return R.success();
    }
}
