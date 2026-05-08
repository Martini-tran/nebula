package com.nebula.common.core.constant;

/**
 * 安全/鉴权相关常量
 * 约定网关 ↔ 业务服务之间通过 header 传递的字段名
 *
 * @author nebula
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    /**
     * 客户端传 token 的请求头（与 sa-token 默认 token-name 保持一致）
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * 网关校验通过后向下游透传的用户 ID
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 网关校验通过后向下游透传的用户名
     */
    public static final String HEADER_USER_NAME = "X-User-Name";

    /**
     * 网关校验通过后向下游透传的角色编码列表（逗号分隔）
     */
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /**
     * 网关校验通过后向下游透传的权限标识列表（逗号分隔）
     */
    public static final String HEADER_USER_PERMS = "X-User-Perms";

    /**
     * 内部调用标识，下游识别后跳过部分校验
     */
    public static final String HEADER_INNER_CALL = "X-Inner-Call";

    /**
     * 超管角色编码
     */
    public static final String ROLE_SUPER_ADMIN = "super_admin";

    /**
     * 超级管理员用户 ID
     */
    public static final Long SUPER_ADMIN_ID = 1L;
}
