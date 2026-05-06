package com.nebula.common.captcha.enums;

import com.nebula.common.core.enums.IResultCode;

/**
 * 验证码模块业务错误码
 *
 * @author nebula
 */
public enum CaptchaResultCode implements IResultCode {

    REPEAT_TOO_FAST(40031, "请求过于频繁，请稍后再试"),
    GENERATE_FAIL(40032, "验证码生成失败"),
    CHECK_FAIL(40033, "验证失败，请重新验证"),
    VERIFY_TOKEN_INVALID(40034, "验证码已失效，请重新获取"),
    TYPE_DISABLED(40035, "该类型验证码未启用"),
    TYPE_UNKNOWN(40036, "不支持的验证码类型");

    private final int code;
    private final String message;

    CaptchaResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
