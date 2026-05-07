package com.nebula.system.enums;

import com.nebula.common.core.enums.IResultCode;

/**
 * service-system 业务错误码
 *
 * @author nebula
 */
public enum SystemResultCode implements IResultCode {

    USER_NOT_FOUND(40201, "用户不存在"),
    USERNAME_EXISTS(40202, "用户名已被使用"),
    MOBILE_EXISTS(40203, "手机号已被使用"),
    EMAIL_EXISTS(40204, "邮箱已被使用"),
    USERNAME_INVALID(40205, "用户名格式不合法"),
    PASSWORD_INVALID(40206, "密码格式不合法"),

    MENU_NOT_FOUND(40301, "菜单不存在"),
    MENU_NAME_EXISTS(40302, "菜单名称已存在"),
    MENU_PATH_EXISTS(40303, "菜单路径已存在"),
    MENU_HAS_CHILDREN(40304, "存在子菜单，无法删除"),
    MENU_TYPE_INVALID(40305, "菜单类型不合法"),
    MENU_PARENT_INVALID(40306, "父级菜单不合法");

    private final int code;
    private final String message;

    SystemResultCode(int code, String message) {
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
