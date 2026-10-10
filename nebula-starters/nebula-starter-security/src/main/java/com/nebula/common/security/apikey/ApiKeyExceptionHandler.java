package com.nebula.common.security.apikey;

import cn.dev33.satoken.apikey.error.SaApiKeyErrorCode;
import cn.dev33.satoken.apikey.exception.ApiKeyException;
import cn.dev33.satoken.apikey.exception.ApiKeyScopeException;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API Key 鉴权异常处理
 * 不处理的话会落到全局兜底，被当成 500 系统异常。响应文案自己写，不透传框架的异常消息——
 * 框架的消息里带着完整的 key，会随日志和响应泄露。
 *
 * @author nebula
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiKeyExceptionHandler {

    /**
     * key 有效，但接口未开放或 scope 不够
     */
    @ExceptionHandler(ApiKeyScopeException.class)
    public R<Void> handleScope(ApiKeyScopeException e) {
        String message = e.getScope() == null
                ? "该接口未开放 API Key 访问"
                : "API Key 缺少授权范围：" + e.getScope();
        log.warn("API Key 越权访问: key={}, scope={}", ApiKeyMasker.mask(e.getApiKey()), e.getScope());
        return R.fail(ResultCode.FORBIDDEN.getCode(), message);
    }

    /**
     * key 本身不可用：无效 / 过期 / 禁用
     */
    @ExceptionHandler(ApiKeyException.class)
    public R<Void> handleInvalid(ApiKeyException e) {
        String message = switch (e.getCode()) {
            case SaApiKeyErrorCode.CODE_12302 -> "API Key 已过期";
            case SaApiKeyErrorCode.CODE_12303 -> "API Key 已被禁用";
            default -> "API Key 无效";
        };
        log.warn("API Key 校验失败: key={}, code={}", ApiKeyMasker.mask(e.getApiKey()), e.getCode());
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), message);
    }
}
