package com.nebula.manager.dto;

import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 验证码类型
     */
    private String captchaType;

    /**
     * 验证码校验令牌
     */
    private String captchaVerifyToken;
}
