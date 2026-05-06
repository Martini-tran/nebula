package com.nebula.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;
    private String password;

    /**
     * 可选：昵称，不填默认使用 username
     */
    private String nickname;
    /**
     * 可选：手机号
     */
    private String mobile;
    /**
     * 可选：邮箱
     */
    private String email;

    /**
     * 验证码类型：blockPuzzle / clickWord
     */
    private String captchaType;
    /**
     * /captcha/check 返回的 verifyToken
     */
    private String captchaVerifyToken;
}
