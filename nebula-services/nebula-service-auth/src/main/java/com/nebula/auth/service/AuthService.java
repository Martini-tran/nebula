package com.nebula.auth.service;

import com.nebula.auth.dto.LoginRequest;
import com.nebula.auth.dto.RegisterRequest;
import com.nebula.system.entity.SysUser;

/**
 * service-auth 业务接口
 *
 * @author nebula
 */
public interface AuthService {

    /**
     * 注册新用户：校验开关 → 校验验证码 → 校验入参/唯一性 → 写库
     */
    SysUser register(RegisterRequest request);

    /**
     * 登录：校验验证码 → 用户名查询 → BCrypt 校验 → Sa-Token 登录
     */
    SysUser login(LoginRequest request);
}
