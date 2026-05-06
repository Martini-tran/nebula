package com.nebula.common.core.context;

/**
 * 当前请求的用户上下文（线程级）
 * 由 starter-security 的拦截器在请求入口处通过网关 header 写入，
 * 业务代码、MP 自动填充等可直接读取。
 *
 * @author nebula
 */
public final class UserContext {

    /**
     * ThreadLocal 存储当前线程的用户信息
     * 确保在多线程环境下每个线程都有独立的用户上下文
     * 避免不同请求之间的用户信息混淆
     */
    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    /**
     * 私有构造函数
     * 防止外部实例化此类，确保 UserContext 类为工具类
     * 所有操作都通过静态方法进行
     */
    private UserContext() {
    }

    /**
     * 设置当前线程的用户信息
     * 在请求开始时由安全拦截器调用，将用户信息存储到当前线程
     *
     * @param userId 用户ID，标识当前操作的用户
     * @param username 用户名，当前操作的用户名
     */
    public static void set(Long userId, String username) {
        HOLDER.set(new UserInfo(userId, username));
    }

    /**
     * 获取当前线程的用户ID
     * 用于业务逻辑中获取当前操作的用户ID
     *
     * @return 返回当前用户的ID，如果未设置则返回null
     */
    public static Long getUserId() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.userId();
    }

    /**
     * 获取当前线程的用户名
     * 用于业务逻辑中获取当前操作的用户名
     *
     * @return 返回当前用户的用户名，如果未设置则返回null
     */
    public static String getUsername() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.username();
    }

    /**
     * 获取当前线程的完整用户信息
     * 返回封装了用户ID和用户名的UserInfo对象
     *
     * @return 返回当前线程的UserInfo对象，如果未设置则返回null
     */
    public static UserInfo get() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的用户信息
     * 在请求结束时调用，释放ThreadLocal中的用户信息
     * 防止内存泄漏，确保线程池中的线程不会携带上一次请求的用户信息
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 用户信息记录类
     * 使用record关键字创建不可变的数据载体类
     * 包含用户ID和用户名两个字段
     */
    public record UserInfo(Long userId, String username) {
    }
}
