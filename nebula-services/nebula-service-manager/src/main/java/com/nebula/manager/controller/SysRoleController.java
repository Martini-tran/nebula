package com.nebula.manager.controller;

import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.RoleCreateRequest;
import com.nebula.manager.dto.RoleMenuAssignRequest;
import com.nebula.manager.dto.RolePageQuery;
import com.nebula.manager.dto.RoleStatusUpdateRequest;
import com.nebula.manager.dto.RoleUpdateRequest;
import com.nebula.manager.dto.RoleUserAssignRequest;
import com.nebula.manager.service.SysRoleService;
import com.nebula.manager.vo.RoleListVO;
import com.nebula.manager.vo.RoleSimpleVO;
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

    @GetMapping("/{id}/menus")
    public R<List<Long>> menus(@PathVariable("id") Long id) {
        return R.success(roleService.listMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable("id") Long id, @RequestBody RoleMenuAssignRequest request) {
        roleService.assignMenus(id, request.getMenuIds());
        return R.success("menus updated", null);
    }

    @GetMapping("/{id}/users")
    public R<List<Long>> users(@PathVariable("id") Long id) {
        return R.success(roleService.listUserIds(id));
    }

    @PutMapping("/{id}/users")
    public R<Void> assignUsers(@PathVariable("id") Long id, @RequestBody RoleUserAssignRequest request) {
        roleService.assignUsers(id, request.getUserIds());
        return R.success("users updated", null);
    }
}
