package com.nebula.manager.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.UserCreateRequest;
import com.nebula.manager.dto.UserPageQuery;
import com.nebula.manager.dto.UserResetPasswordRequest;
import com.nebula.manager.dto.UserStatusUpdateRequest;
import com.nebula.manager.dto.UserUpdateRequest;
import com.nebula.manager.service.SysUserService;
import com.nebula.manager.vo.UserDetailVO;
import com.nebula.manager.vo.UserListVO;
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
 * 系统用户控制器
 * 提供用户的分页查询、详情查看、创建、更新、删除以及状态和密码管理等功能
 *
 * @author nebula
 */
@RestController
@RequestMapping("/user")
public class SysUserController {

    /**
     * 用户服务业务层注入
     * 负责处理用户相关的业务逻辑
     */
    private final SysUserService userService;

    /**
     * 构造函数注入用户服务
     *
     * @param userService 用户服务实例
     */
    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户列表
     * 根据查询条件返回用户分页数据
     *
     * @param query 用户分页查询条件
     * @return 分页结果，包含用户列表和分页信息
     */
    @GetMapping("/page")
    public R<PageResult<UserListVO>> page(@ModelAttribute UserPageQuery query) {
        return R.success(userService.page(query));
    }

    /**
     * 获取用户详情
     * 根据用户ID查询指定用户的详细信息
     *
     * @param id 用户ID路径参数
     * @return 用户详细信息
     */
    @GetMapping("/{id}")
    public R<UserDetailVO> detail(@PathVariable("id") Long id) {
        return R.success(userService.detail(id));
    }

    /**
     * 创建新用户
     * 接收用户创建请求数据，保存新的用户记录
     *
     * @param request 用户创建请求对象，包含用户基本信息
     * @return 创建成功的用户ID
     */
    @SaCheckPermission("system:user:add")
    @PostMapping
    public R<Long> create(@RequestBody UserCreateRequest request) {
        return R.success("create success", userService.create(request));
    }

    /**
     * 更新用户信息
     * 根据用户ID修改指定用户的数据
     *
     * @param id      用户ID路径参数
     * @param request 用户更新请求对象，包含要修改的字段
     * @return 更新操作结果
     */
    @SaCheckPermission("system:user:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody UserUpdateRequest request) {
        // 调用用户服务执行更新操作
        userService.update(id, request);
        // 返回更新成功响应
        return R.success("update success", null);
    }

    /**
     * 删除用户
     * 根据用户ID删除对应的用户记录
     *
     * @param id 用户ID路径参数
     * @return 删除操作结果
     */
    @SaCheckPermission("system:user:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        // 调用用户服务执行删除操作
        userService.delete(id);
        // 返回删除成功响应
        return R.success("delete success", null);
    }

    /**
     * 更新用户状态
     * 修改用户的启用/禁用状态
     *
     * @param id      用户ID路径参数
     * @param request 用户状态更新请求对象，包含新的状态值
     * @return 状态更新操作结果
     */
    @SaCheckPermission("system:user:edit")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody UserStatusUpdateRequest request) {
        // 调用用户服务更新用户状态
        userService.updateStatus(id, request.getStatus());
        // 返回状态更新成功响应
        return R.success("status updated", null);
    }

    /**
     * 重置用户密码
     * 为指定用户设置新的密码
     *
     * @param id      用户ID路径参数
     * @param request 用户密码重置请求对象，包含新密码
     * @return 密码重置操作结果
     */
    @SaCheckPermission("system:user:edit")
    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable("id") Long id, @RequestBody UserResetPasswordRequest request) {
        // 调用用户服务重置用户密码
        userService.resetPassword(id, request.getNewPassword());
        // 返回密码重置成功响应
        return R.success("password reset", null);
    }
}
