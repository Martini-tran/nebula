package com.nebula.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {

    private String username;
    private String password;

    /** 验证码类型：blockPuzzle / clickWord */
    private String captchaType;
    /** /captcha/check 返回的 verifyToken */
    private String captchaVerifyToken;
}
