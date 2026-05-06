package com.nebula.common.core.exception;

import com.nebula.common.core.enums.IResultCode;
import com.nebula.common.core.enums.ResultCode;
import java.io.Serial;
import lombok.Getter;

/**
 * 业务异常
 * service 层在出现可预期的业务错误时抛出，由全局异常处理器统一捕获并转成 {@link com.nebula.common.core.domain.R}
 *
 * @author nebula
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(IResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_SERVER_ERROR.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
