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
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理用户注册、登录、登出和会话信息相关的API请求
 *
 * @author nebula
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * 认证服务业务层注入
     * 负责处理具体的认证业务逻辑
     */
    private final AuthService authService;

    /**
     * 构造函数注入认证服务
     *
     * @param authService 认证服务实例
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册接口
     * 接收注册请求数据，调用认证服务完成注册操作
     *
     * @param request 注册请求对象，包含用户名、密码等信息
     * @return 注册响应结果，包含用户ID、用户名和昵称
     */
    @PostMapping("/register")
    public R<RegisterResponse> register(@RequestBody RegisterRequest request) {
        // 调用认证服务执行注册逻辑
        SysUser user = authService.register(request);
        // 返回成功响应，包含注册成功的用户信息
        return R.success("register success",
                new RegisterResponse(user.getId(), user.getUsername(), user.getNickname()));
    }

    /**
     * 用户登录接口
     * 接收登录请求数据，验证用户身份并返回登录凭证
     *
     * @param request 登录请求对象，包含用户名和密码
     * @return 登录响应结果，包含用户信息和认证令牌
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        // 调用认证服务执行登录验证
        SysUser user = authService.login(request);
        // 返回登录成功响应，包含用户信息和Token信息
        return R.success("login success", new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                StpUtil.getTokenName(),      // 获取Token名称
                StpUtil.getTokenValue()));   // 获取Token值
    }

    /**
     * 用户登出接口
     * 清除当前用户的登录状态和认证信息
     *
     * @return 登出操作结果
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        // 调用Sa-Token工具清除当前用户登录状态
        StpUtil.logout();
        // 返回登出成功响应
        return R.success("logout success", null);
    }

    /**
     * 获取会话信息接口
     * 查询当前用户的登录状态和会话相关信息
     *
     * @return 会话信息响应结果
     */
    @GetMapping("/session")
    public R<SessionResponse> session() {
        // 检查当前用户是否已登录
        boolean isLogin = StpUtil.isLogin();
        // 根据登录状态返回相应的会话信息
        return R.success(new SessionResponse(
                isLogin,                                      // 登录状态
                isLogin ? StpUtil.getLoginIdDefaultNull() : null, // 登录ID，未登录则为null
                isLogin ? StpUtil.getTokenTimeout() : null)); // Token剩余有效期，未登录则为null
    }
}
