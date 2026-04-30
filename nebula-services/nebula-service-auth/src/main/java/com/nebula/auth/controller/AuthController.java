package com.nebula.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.nebula.auth.model.LoginRequest;
import com.nebula.common.core.domain.R;
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

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.getLoginId())) {
            return R.fail(400, "loginId must not be blank");
        }
        StpUtil.login(request.getLoginId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginId", request.getLoginId());
        payload.put("tokenName", StpUtil.getTokenName());
        payload.put("tokenValue", StpUtil.getTokenValue());
        payload.put("isLogin", StpUtil.isLogin());
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
        payload.put("isLogin", StpUtil.isLogin());
        payload.put("loginId", StpUtil.isLogin() ? StpUtil.getLoginIdDefaultNull() : null);
        payload.put("tokenInfo", StpUtil.isLogin() ? StpUtil.getTokenInfo() : SaResult.error("not login").getData());
        return R.success(payload);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
