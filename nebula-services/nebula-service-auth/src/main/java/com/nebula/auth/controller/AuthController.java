package com.nebula.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.nebula.auth.model.LoginRequest;
import com.nebula.auth.model.RegisterRequest;
import com.nebula.auth.service.AuthService;
import com.nebula.common.core.domain.R;
import com.nebula.system.entity.SysUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        SysUser user = authService.register(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("nickname", user.getNickname());
        return R.success("register success", payload);
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest request) {
        SysUser user = authService.login(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("nickname", user.getNickname());
        payload.put("tokenName", StpUtil.getTokenName());
        payload.put("tokenValue", StpUtil.getTokenValue());
        return R.success("login success", payload);
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.success("logout success", null);
    }

    @GetMapping("/session")
    public R<Map<String, Object>> session() {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean isLogin = StpUtil.isLogin();
        payload.put("isLogin", isLogin);
        payload.put("loginId", isLogin ? StpUtil.getLoginIdDefaultNull() : null);
        payload.put("tokenInfo", isLogin ? StpUtil.getTokenInfo() : null);
        return R.success(payload);
    }
}
