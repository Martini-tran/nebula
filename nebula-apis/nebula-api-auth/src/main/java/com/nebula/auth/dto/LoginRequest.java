package com.nebula.auth.dto;

import lombok.Data;

/**
 * 登录请求 DTO
 *
 * @author nebula
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
     * 验证码类型：blockPuzzle / clickWord
     */
    private String captchaType;
    /**
     * /captcha/check 返回的 verifyToken
     */
    private String captchaVerifyToken;
}
