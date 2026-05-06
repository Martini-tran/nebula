package com.nebula.auth.constant;

/**
 * service-auth 从 sys_config 读取的配置 key 与默认值
 *
 * @author nebula
 */
public final class AuthConfigKeys {

    private AuthConfigKeys() {
    }

    /**
     * 是否开启注册，默认关闭
     */
    public static final String REGISTER_ENABLED = "auth.register.enabled";
    public static final boolean DEFAULT_REGISTER_ENABLED = false;

    /**
     * 用户名最小长度
     */
    public static final String USERNAME_MIN_LENGTH = "auth.username.min.length";
    public static final int DEFAULT_USERNAME_MIN_LENGTH = 4;

    /**
     * 用户名最大长度
     */
    public static final String USERNAME_MAX_LENGTH = "auth.username.max.length";
    public static final int DEFAULT_USERNAME_MAX_LENGTH = 32;

    /**
     * 密码最小长度
     */
    public static final String PASSWORD_MIN_LENGTH = "auth.password.min.length";
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 6;

    /**
     * 密码最大长度
     */
    public static final String PASSWORD_MAX_LENGTH = "auth.password.max.length";
    public static final int DEFAULT_PASSWORD_MAX_LENGTH = 64;
}
