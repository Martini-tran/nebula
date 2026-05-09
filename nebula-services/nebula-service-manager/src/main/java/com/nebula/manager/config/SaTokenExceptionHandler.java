package com.nebula.manager.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 鉴权异常处理
 * 优先级高于通用全局处理器，避免 NotPermissionException / NotRoleException 落到 500 兜底分支
 *
 * @author nebula
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    /**
     * 未登录或 token 失效（理论上网关已拦，作为业务侧二次防御）
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("Sa-Token 未登录: type={}, msg={}", e.getType(), e.getMessage());
        ResultCode code = switch (e.getType()) {
            case NotLoginException.NOT_TOKEN, NotLoginException.INVALID_TOKEN -> ResultCode.TOKEN_INVALID;
            case NotLoginException.TOKEN_TIMEOUT -> ResultCode.TOKEN_EXPIRED;
            default -> ResultCode.UNAUTHORIZED;
        };
        return R.fail(code);
    }

    /**
     * 缺少权限
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("缺少权限: {}", e.getPermission());
        return R.fail(ResultCode.FORBIDDEN.getCode(), "无权访问，缺少权限：" + e.getPermission());
    }

    /**
     * 缺少角色
     */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("缺少角色: {}", e.getRole());
        return R.fail(ResultCode.FORBIDDEN.getCode(), "无权访问，缺少角色：" + e.getRole());
    }
}
