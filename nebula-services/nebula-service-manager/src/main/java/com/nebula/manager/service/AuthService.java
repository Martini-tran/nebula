package com.nebula.manager.service;

import com.nebula.manager.dto.LoginRequest;
import com.nebula.manager.dto.RegisterRequest;
import com.nebula.system.entity.SysUser;

/**
 * 认证服务接口
 * 定义用户注册和登录相关的业务方法
 *
 * @author nebula
 */
public interface AuthService {

    /**
     * 用户注册方法
     * 根据注册请求数据创建新用户账户
     *
     * @param request 注册请求对象，包含用户名、密码等注册信息
     * @return 创建成功的用户实体对象
     */
    SysUser register(RegisterRequest request);

    /**
     * 用户登录方法
     * 验证用户登录凭据并返回用户信息
     *
     * @param request 登录请求对象，包含用户名和密码
     * @return 登录成功的用户实体对象
     */
    SysUser login(LoginRequest request);
}
