package com.nebula.manager.constant;

/**
 * 系统管理配置项常量类
 * 用于定义系统中各种配置项的键名和默认值
 * 包含用户认证、用户名密码长度限制等相关配置
 *
 * @author nebula
 */
public final class ManagerConfigKeys {

    /**
     * 私有构造函数，防止实例化此类
     * 由于此类仅用于存储常量，因此不允许创建实例
     */
    private ManagerConfigKeys() {
    }

    /**
     * 注册功能启用配置键
     * 用于控制是否允许新用户注册
     */
    public static final String REGISTER_ENABLED = "auth.register.enabled";
    /**
     * 注册功能默认状态
     * 默认情况下禁用注册功能
     */
    public static final boolean DEFAULT_REGISTER_ENABLED = false;

    /**
     * 用户名最小长度配置键
     * 用于设定用户名的最小字符数限制
     */
    public static final String USERNAME_MIN_LENGTH = "auth.username.min.length";
    /**
     * 用户名最小长度默认值
     * 默认最小长度为4个字符
     */
    public static final int DEFAULT_USERNAME_MIN_LENGTH = 4;

    /**
     * 用户名最大长度配置键
     * 用于设定用户名的最大字符数限制
     */
    public static final String USERNAME_MAX_LENGTH = "auth.username.max.length";
    /**
     * 用户名最大长度默认值
     * 默认最大长度为32个字符
     */
    public static final int DEFAULT_USERNAME_MAX_LENGTH = 32;

    /**
     * 密码最小长度配置键
     * 用于设定密码的最小字符数限制
     */
    public static final String PASSWORD_MIN_LENGTH = "auth.password.min.length";
    /**
     * 密码最小长度默认值
     * 默认最小长度为6个字符
     */
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 6;

    /**
     * 密码最大长度配置键
     * 用于设定密码的最大字符数限制
     */
    public static final String PASSWORD_MAX_LENGTH = "auth.password.max.length";
    /**
     * 密码最大长度默认值
     * 默认最大长度为64个字符
     */
    public static final int DEFAULT_PASSWORD_MAX_LENGTH = 64;
}
