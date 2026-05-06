package com.nebula.common.core.domain;

import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.enums.IResultCode;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一API响应结果封装类
 * 用于规范前后端交互的数据格式，包含状态码、消息和响应数据
 *
 * @param <T> 响应数据的类型
 * @author nebula
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码
     * 200表示成功，其他值表示失败或特定业务状态
     */
    private final int code;

    /**
     * 响应消息
     * 成功时通常为"ok"，失败时为具体的错误描述
     */
    private final String message;

    /**
     * 响应数据
     * 成功时返回业务数据，失败时通常为null
     */
    private final T data;

    /**
     * 成功响应（默认消息"ok"）
     *
     * @param data 响应数据，可为null
     * @param <T>  数据类型
     * @return 成功响应对象，状态码为200，消息为"ok"
     */
    public static <T> R<T> success(T data) {
        return new R<>(HttpStatus.SUCCESS, "ok", data);
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
        return new R<>(HttpStatus.SUCCESS, message, data);
    }

    /**
     * 失败响应（默认状态码500）
     *
     * @param message 失败原因描述
     * @param <T>     数据类型（通常为Object，因为data为null）
     * @return 失败响应对象，状态码为500，data为null
     */
    public static <T> R<T> fail(String message) {
        return new R<>(HttpStatus.INTERNAL_SERVER_ERROR, message, null);
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
     * 失败响应（基于业务错误码枚举）
     *
     * @param resultCode 实现 {@link IResultCode} 的错误码枚举
     * @param <T>        数据类型
     * @return 失败响应对象，data为null
     */
    public static <T> R<T> fail(IResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return code == HttpStatus.SUCCESS;
    }
}