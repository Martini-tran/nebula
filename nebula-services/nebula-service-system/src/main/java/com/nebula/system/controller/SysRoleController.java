package com.nebula.system.controller;

import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.system.dto.RoleCreateRequest;
import com.nebula.system.dto.RoleMenuAssignRequest;
import com.nebula.system.dto.RolePageQuery;
import com.nebula.system.dto.RoleStatusUpdateRequest;
import com.nebula.system.dto.RoleUpdateRequest;
import com.nebula.system.dto.RoleUserAssignRequest;
import com.nebula.system.service.SysRoleService;
import com.nebula.system.vo.RoleListVO;
import com.nebula.system.vo.RoleSimpleVO;
import java.util.List;
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
 * 角色管理（context-path=/system，最终访问路径 /system/role/...）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/role")
public class SysRoleController {

    private final SysRoleService roleService;

    public SysRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/page")
    public R<PageResult<RoleListVO>> page(@ModelAttribute RolePageQuery query) {
        return R.success(roleService.page(query));
    }

    /**
     * 启用状态下的全量角色，供下拉、勾选场景使用
     */
    @GetMapping("/list")
    public R<List<RoleSimpleVO>> list() {
        return R.success(roleService.listEnabled());
    }

    @GetMapping("/{id}")
    public R<RoleListVO> detail(@PathVariable("id") Long id) {
        return R.success(roleService.detail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody RoleCreateRequest request) {
        return R.success("create success", roleService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody RoleUpdateRequest request) {
        roleService.update(id, request);
        return R.success("update success", null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return R.success("delete success", null);
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody RoleStatusUpdateRequest request) {
        roleService.updateStatus(id, request.getStatus());
        return R.success("status updated", null);
    }

    /**
     * 取角色当前授权的菜单 ID
     */
    @GetMapping("/{id}/menus")
    public R<List<Long>> menus(@PathVariable("id") Long id) {
        return R.success(roleService.listMenuIds(id));
    }

    /**
     * 全量覆盖角色 - 菜单关联
     */
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable("id") Long id, @RequestBody RoleMenuAssignRequest request) {
        roleService.assignMenus(id, request.getMenuIds());
        return R.success("menus updated", null);
    }

    /**
     * 取角色当前绑定的用户 ID
     */
    @GetMapping("/{id}/users")
    public R<List<Long>> users(@PathVariable("id") Long id) {
        return R.success(roleService.listUserIds(id));
    }

    /**
     * 全量覆盖角色 - 用户关联
     */
    @PutMapping("/{id}/users")
    public R<Void> assignUsers(@PathVariable("id") Long id, @RequestBody RoleUserAssignRequest request) {
        roleService.assignUsers(id, request.getUserIds());
        return R.success("users updated", null);
    }
}
