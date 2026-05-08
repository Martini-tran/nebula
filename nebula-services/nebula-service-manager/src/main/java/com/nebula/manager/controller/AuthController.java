package com.nebula.manager.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.LoginRequest;
import com.nebula.manager.dto.RegisterRequest;
import com.nebula.manager.service.AuthService;
import com.nebula.manager.vo.LoginResponse;
import com.nebula.manager.vo.RegisterResponse;
import com.nebula.manager.vo.SessionResponse;
import com.nebula.system.entity.SysUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public R<RegisterResponse> register(@RequestBody RegisterRequest request) {
        SysUser user = authService.register(request);
        return R.success("register success",
                new RegisterResponse(user.getId(), user.getUsername(), user.getNickname()));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        SysUser user = authService.login(request);
        return R.success("login success", new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                StpUtil.getTokenName(),
                StpUtil.getTokenValue()));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.success("logout success", null);
    }

    @GetMapping("/session")
    public R<SessionResponse> session() {
        boolean isLogin = StpUtil.isLogin();
        return R.success(new SessionResponse(
                isLogin,
                isLogin ? StpUtil.getLoginIdDefaultNull() : null,
                isLogin ? StpUtil.getTokenTimeout() : null));
    }
}
