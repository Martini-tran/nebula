package com.nebula.system.dto;

import lombok.Data;

/**
 * 管理员重置密码入参
 *
 * @author nebula
 */
@Data
public class UserResetPasswordRequest {

    /**
     * 新密码（明文，后端 BCrypt 加密后落库）
     */
    private String newPassword;
}
