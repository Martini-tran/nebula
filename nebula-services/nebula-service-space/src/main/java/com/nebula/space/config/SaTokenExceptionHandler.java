package com.nebula.space.config;

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
 * 空间服务的 Sa-Token 鉴权异常处理器
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("Sa-Token not login, type={}, msg={}", e.getType(), e.getMessage());
        ResultCode code = switch (e.getType()) {
            case NotLoginException.NOT_TOKEN, NotLoginException.INVALID_TOKEN -> ResultCode.TOKEN_INVALID;
            case NotLoginException.TOKEN_TIMEOUT -> ResultCode.TOKEN_EXPIRED;
            default -> ResultCode.UNAUTHORIZED;
        };
        return R.fail(code);
    }

    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("Sa-Token permission denied: {}", e.getPermission());
        return R.fail(ResultCode.FORBIDDEN.getCode(), "No permission: " + e.getPermission());
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("Sa-Token role denied: {}", e.getRole());
        return R.fail(ResultCode.FORBIDDEN.getCode(), "No role: " + e.getRole());
    }
}
