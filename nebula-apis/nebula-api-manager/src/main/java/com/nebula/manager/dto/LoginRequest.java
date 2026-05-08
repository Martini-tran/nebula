package com.nebula.manager.dto;

import lombok.Data;

@Data
public class LoginRequest {

    private String username;
    private String password;
    private String captchaType;
    private String captchaVerifyToken;
}
