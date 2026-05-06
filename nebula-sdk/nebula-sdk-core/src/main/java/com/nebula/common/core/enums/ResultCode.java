package com.nebula.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用业务错误码
 * 范围划分：
 *   2xx / 4xx / 5xx 与 HTTP 语义对齐，用于框架级错误
 *   1xxxx 系列保留给各业务模块自定义错误码（如 auth: 10xxx, system: 11xxx）
 *
 * @author nebula
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements IResultCode {

    SUCCESS(200, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    PARAM_INVALID(422, "参数校验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    USERNAME_OR_PASSWORD_ERROR(10001, "用户名或密码错误"),
    USER_DISABLED(10002, "用户已被禁用"),
    USER_NOT_FOUND(10003, "用户不存在"),
    TOKEN_INVALID(10004, "token 无效"),
    TOKEN_EXPIRED(10005, "token 已过期"),

    ;

    private final int code;
    private final String message;
}
