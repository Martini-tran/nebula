package com.nebula.common.core.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一API响应结果封装类
 * <p>用于规范前后端交互的数据格式，包含状态码、消息和响应数据</p>
 *
 * @param <T> 响应数据的类型
 * @author nebula
 */
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码
     * <p>200表示成功，其他值表示失败或特定业务状态</p>
     */
    private final int code;

    /**
     * 响应消息
     * <p>成功时通常为"ok"，失败时为具体的错误描述</p>
     */
    private final String message;

    /**
     * 响应数据
     * <p>成功时返回业务数据，失败时通常为null</p>
     */
    private final T data;

    /**
     * 私有构造函数，禁止外部直接创建实例
     * <p>通过静态工厂方法 {@link #success(Object)}、{@link #success(String, Object)} 或 {@link #fail(int, String)} 创建</p>
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     */
    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应（默认消息"ok"）
     *
     * @param data 响应数据，可为null
     * @param <T>  数据类型
     * @return 成功响应对象，状态码为200，消息为"ok"
     */
    public static <T> R<T> success(T data) {
        return new R<>(200, "ok", data);
    }

    /**
     * 成功响应（自定义消息）
     *
     * @param message 自定义成功消息
     * @param data    响应数据，可为null
     * @param <T>     数据类型
     * @return 成功响应对象，状态码为200
     */
    public static <T> R<T> success(String message, T data) {
        return new R<>(200, message, data);
    }

    /**
     * 失败响应
     *
     * @param code    失败状态码（非200）
     * @param message 失败原因描述
     * @param <T>     数据类型（通常为Object，因为data为null）
     * @return 失败响应对象，data为null
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 获取响应状态码
     *
     * @return 状态码，200表示成功
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取响应消息
     *
     * @return 响应消息内容
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取响应数据
     *
     * @return 响应数据，失败时为null
     */
    public T getData() {
        return data;
    }
}