package com.nebula.manager.controller;

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

@RestController
@RequestMapping("/user")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/page")
    public R<PageResult<UserListVO>> page(@ModelAttribute UserPageQuery query) {
        return R.success(userService.page(query));
    }

    @GetMapping("/{id}")
    public R<UserDetailVO> detail(@PathVariable("id") Long id) {
        return R.success(userService.detail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody UserCreateRequest request) {
        return R.success("create success", userService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody UserUpdateRequest request) {
        userService.update(id, request);
        return R.success("update success", null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return R.success("delete success", null);
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody UserStatusUpdateRequest request) {
        userService.updateStatus(id, request.getStatus());
        return R.success("status updated", null);
    }

    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable("id") Long id, @RequestBody UserResetPasswordRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return R.success("password reset", null);
    }
}
