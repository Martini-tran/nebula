package com.nebula.common.core.constant;

/**
 * HTTP 状态码常量
 * 用于 {@link com.nebula.common.core.domain.R} 等统一响应封装的状态码取值
 *
 * @author nebula
 */
public final class HttpStatus {

    private HttpStatus() {
    }

    /**
     * 操作成功
     */
    public static final int SUCCESS = 200;

    /**
     * 资源已创建
     */
    public static final int CREATED = 201;

    /**
     * 操作已接受，正在处理
     */
    public static final int ACCEPTED = 202;

    /**
     * 操作成功但无返回内容
     */
    public static final int NO_CONTENT = 204;

    /**
     * 资源已永久移动
     */
    public static final int MOVED_PERMANENTLY = 301;

    /**
     * 资源未修改，可使用缓存
     */
    public static final int NOT_MODIFIED = 304;

    /**
     * 请求参数错误
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 未认证或认证失效
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 已认证但无权限访问
     */
    public static final int FORBIDDEN = 403;

    /**
     * 资源不存在
     */
    public static final int NOT_FOUND = 404;

    /**
     * 请求方法不允许
     */
    public static final int METHOD_NOT_ALLOWED = 405;

    /**
     * 资源冲突
     */
    public static final int CONFLICT = 409;

    /**
     * 不支持的媒体类型
     */
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;

    /**
     * 请求参数校验失败
     */
    public static final int UNPROCESSABLE_ENTITY = 422;

    /**
     * 请求过于频繁，已触发限流
     */
    public static final int TOO_MANY_REQUESTS = 429;

    /**
     * 服务器内部错误
     */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * 服务未实现
     */
    public static final int NOT_IMPLEMENTED = 501;

    /**
     * 网关错误
     */
    public static final int BAD_GATEWAY = 502;

    /**
     * 服务不可用
     */
    public static final int SERVICE_UNAVAILABLE = 503;

    /**
     * 网关超时
     */
    public static final int GATEWAY_TIMEOUT = 504;
}
