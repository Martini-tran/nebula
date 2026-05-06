package com.nebula.auth.enums;

import com.nebula.common.core.enums.IResultCode;

/**
 * service-auth 业务错误码
 *
 * @author nebula
 */
public enum AuthResultCode implements IResultCode {

    REGISTER_DISABLED(40101, "注册功能未开启"),
    USERNAME_INVALID(40102, "用户名格式不合法"),
    PASSWORD_INVALID(40103, "密码格式不合法"),
    USERNAME_EXISTS(40104, "用户名已被使用"),
    EMAIL_EXISTS(40105, "邮箱已被使用"),
    MOBILE_EXISTS(40106, "手机号已被使用"),
    USER_NOT_FOUND(40107, "用户不存在"),
    PASSWORD_MISMATCH(40108, "用户名或密码错误"),
    USER_DISABLED(40109, "账号已被禁用");

    private final int code;
    private final String message;

    AuthResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
