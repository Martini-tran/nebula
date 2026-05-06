package com.nebula.common.core.enums;

/**
 * 业务错误码统一接口
 * 所有业务错误码枚举都应实现此接口，便于在 {@link com.nebula.common.core.domain.R}
 * 与 {@link com.nebula.common.core.exception.BizException} 中统一传递
 *
 * @author nebula
 */
public interface IResultCode {

    /**
     * 错误码
     */
    int getCode();

    /**
     * 错误描述
     */
    String getMessage();
}
