package com.nebula.common.core.context;

/**
 * 当前请求的用户上下文（线程级）
 * 由 starter-security 的拦截器在请求入口处通过网关 header 写入，
 * 业务代码、MP 自动填充等可直接读取。
 *
 * @author nebula
 */
public final class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String username) {
        HOLDER.set(new UserInfo(userId, username));
    }

    public static Long getUserId() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.userId();
    }

    public static String getUsername() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.username();
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record UserInfo(Long userId, String username) {
    }
}
