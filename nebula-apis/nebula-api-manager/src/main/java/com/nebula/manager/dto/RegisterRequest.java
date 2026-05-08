package com.nebula.manager.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;
    private String password;
    private String nickname;
    private String mobile;
    private String email;
    private String captchaType;
    private String captchaVerifyToken;
}
