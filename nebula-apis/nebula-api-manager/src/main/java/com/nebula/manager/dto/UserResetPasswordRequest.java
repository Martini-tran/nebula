package com.nebula.manager.dto;

import lombok.Data;

/**
 * 用户重置密码请求DTO
 */
@Data
public class UserResetPasswordRequest {

    /**
     * 新密码
     */
    private String newPassword;
}
