package com.nebula.manager.enums;

import com.nebula.common.core.enums.IResultCode;

/**
 * 管理模块结果码枚举
 */
public enum ManagerResultCode implements IResultCode {

    // Auth
    /** 用户名或密码错误 */
    USERNAME_OR_PASSWORD_ERROR(40101, "用户名或密码错误"),
    /** 账号已被禁用 */
    USER_DISABLED(40102, "账号已被禁用"),
    /** 验证码校验失败 */
    CAPTCHA_VERIFY_FAILED(40103, "验证码校验失败"),

    // User
    /** 用户不存在 */
    USER_NOT_FOUND(40201, "用户不存在"),
    /** 用户名已被使用 */
    USERNAME_EXISTS(40202, "用户名已被使用"),
    /** 手机号已被使用 */
    MOBILE_EXISTS(40203, "手机号已被使用"),
    /** 邮箱已被使用 */
    EMAIL_EXISTS(40204, "邮箱已被使用"),
    /** 用户名格式不合法 */
    USERNAME_INVALID(40205, "用户名格式不合法"),
    /** 密码格式不合法 */
    PASSWORD_INVALID(40206, "密码格式不合法"),

    // Menu
    /** 菜单不存在 */
    MENU_NOT_FOUND(40301, "菜单不存在"),
    /** 菜单名称已存在 */
    MENU_NAME_EXISTS(40302, "菜单名称已存在"),
    /** 菜单路径已存在 */
    MENU_PATH_EXISTS(40303, "菜单路径已存在"),
    /** 存在子菜单，无法删除 */
    MENU_HAS_CHILDREN(40304, "存在子菜单，无法删除"),
    /** 菜单类型不合法 */
    MENU_TYPE_INVALID(40305, "菜单类型不合法"),
    /** 父级菜单不合法 */
    MENU_PARENT_INVALID(40306, "父级菜单不合法"),

    // Role
    /** 角色不存在 */
    ROLE_NOT_FOUND(40401, "角色不存在"),
    /** 角色编码已存在 */
    ROLE_CODE_EXISTS(40402, "角色编码已存在"),
    /** 角色名称已存在 */
    ROLE_NAME_EXISTS(40403, "角色名称已存在"),
    /** 角色编码格式不合法 */
    ROLE_CODE_INVALID(40404, "角色编码格式不合法"),
    /** 存在已绑定该角色的用户，无法删除 */
    ROLE_HAS_USERS(40405, "存在已绑定该角色的用户，无法删除"),
    /** 超级管理员角色不允许此操作 */
    ROLE_SUPER_ADMIN_FORBIDDEN(40406, "超级管理员角色不允许此操作");

    /** 结果码 */
    private final int code;
    /** 结果消息 */
    private final String message;

    ManagerResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
