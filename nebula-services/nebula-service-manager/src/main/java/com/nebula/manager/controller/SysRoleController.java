package com.nebula.manager.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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

/**
 * 系统角色控制器
 * 提供角色的分页查询、详情查看、创建、更新、删除以及权限分配等功能
 *
 * @author nebula
 */
@RestController
@RequestMapping("/role")
public class SysRoleController {

    /**
     * 角色服务业务层注入
     * 负责处理角色相关的业务逻辑
     */
    private final SysRoleService roleService;

    /**
     * 构造函数注入角色服务
     *
     * @param roleService 角色服务实例
     */
    public SysRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查询角色列表
     * 根据查询条件返回角色分页数据
     *
     * @param query 角色分页查询条件
     * @return 分页结果，包含角色列表和分页信息
     */
    @GetMapping("/page")
    public R<PageResult<RoleListVO>> page(@ModelAttribute RolePageQuery query) {
        return R.success(roleService.page(query));
    }

    /**
     * 获取启用的角色列表
     * 返回所有启用状态的角色简要信息
     *
     * @return 启用角色列表
     */
    @GetMapping("/list")
    public R<List<RoleSimpleVO>> list() {
        return R.success(roleService.listEnabled());
    }

    /**
     * 获取角色详情
     * 根据角色ID查询指定角色的详细信息
     *
     * @param id 角色ID路径参数
     * @return 角色详细信息
     */
    @GetMapping("/{id}")
    public R<RoleListVO> detail(@PathVariable("id") Long id) {
        return R.success(roleService.detail(id));
    }

    /**
     * 创建新角色
     * 接收角色创建请求数据，保存新的角色记录
     *
     * @param request 角色创建请求对象，包含角色基本信息
     * @return 创建成功的角色ID
     */
    @SaCheckPermission("system:role:add")
    @PostMapping
    public R<Long> create(@RequestBody RoleCreateRequest request) {
        return R.success("create success", roleService.create(request));
    }

    /**
     * 更新角色信息
     * 根据角色ID修改指定角色的数据
     *
     * @param id      角色ID路径参数
     * @param request 角色更新请求对象，包含要修改的字段
     * @return 更新操作结果
     */
    @SaCheckPermission("system:role:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody RoleUpdateRequest request) {
        // 调用角色服务执行更新操作
        roleService.update(id, request);
        // 返回更新成功响应
        return R.success("update success", null);
    }

    /**
     * 删除角色
     * 根据角色ID删除对应的角色记录
     *
     * @param id 角色ID路径参数
     * @return 删除操作结果
     */
    @SaCheckPermission("system:role:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        // 调用角色服务执行删除操作
        roleService.delete(id);
        // 返回删除成功响应
        return R.success("delete success", null);
    }

    /**
     * 更新角色状态
     * 修改角色的启用/禁用状态
     *
     * @param id      角色ID路径参数
     * @param request 角色状态更新请求对象，包含新的状态值
     * @return 状态更新操作结果
     */
    @SaCheckPermission("system:role:edit")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody RoleStatusUpdateRequest request) {
        // 调用角色服务更新角色状态
        roleService.updateStatus(id, request.getStatus());
        // 返回状态更新成功响应
        return R.success("status updated", null);
    }

    /**
     * 获取角色关联的菜单ID列表
     * 查询指定角色所拥有的菜单权限ID
     *
     * @param id 角色ID路径参数
     * @return 菜单ID列表
     */
    @GetMapping("/{id}/menus")
    public R<List<Long>> menus(@PathVariable("id") Long id) {
        return R.success(roleService.listMenuIds(id));
    }

    /**
     * 为角色分配菜单权限
     * 将指定的菜单ID列表分配给角色
     *
     * @param id      角色ID路径参数
     * @param request 角色菜单分配请求对象，包含要分配的菜单ID列表
     * @return 菜单分配操作结果
     */
    @SaCheckPermission("system:role:edit")
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable("id") Long id, @RequestBody RoleMenuAssignRequest request) {
        // 调用角色服务为角色分配菜单权限
        roleService.assignMenus(id, request.getMenuIds());
        // 返回菜单分配成功响应
        return R.success("menus updated", null);
    }

    /**
     * 获取角色关联的用户ID列表
     * 查询指定角色所拥有的用户ID
     *
     * @param id 角色ID路径参数
     * @return 用户ID列表
     */
    @GetMapping("/{id}/users")
    public R<List<Long>> users(@PathVariable("id") Long id) {
        return R.success(roleService.listUserIds(id));
    }

    /**
     * 为角色分配用户
     * 将指定的用户ID列表分配给角色
     *
     * @param id      角色ID路径参数
     * @param request 角色用户分配请求对象，包含要分配的用户ID列表
     * @return 用户分配操作结果
     */
    @SaCheckPermission("system:role:edit")
    @PutMapping("/{id}/users")
    public R<Void> assignUsers(@PathVariable("id") Long id, @RequestBody RoleUserAssignRequest request) {
        // 调用角色服务为角色分配用户
        roleService.assignUsers(id, request.getUserIds());
        // 返回用户分配成功响应
        return R.success("users updated", null);
    }
}
