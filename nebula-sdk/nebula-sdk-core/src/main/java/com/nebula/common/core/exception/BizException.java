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

    /**
     * 异常状态码
     * 用于标识具体的错误类型，便于前端处理不同的业务异常情况
     * 通常与IResultCode枚举中的状态码对应
     */
    private final int code;

    /**
     * 构造函数 - 使用结果码创建业务异常
     * 通过IResultCode接口提供的状态码和消息创建异常对象
     *
     * @param resultCode 结果码枚举，包含状态码和默认错误消息
     */
    public BizException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 构造函数 - 使用结果码和自定义消息创建业务异常
     * 保留原有的结果码，但允许自定义错误消息
     *
     * @param resultCode 结果码枚举，包含状态码
     * @param message 自定义错误消息，覆盖resultCode中的默认消息
     */
    public BizException(IResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 构造函数 - 使用自定义消息创建业务异常
     * 使用默认的服务器内部错误状态码，适用于未明确分类的错误
     *
     * @param message 自定义错误消息，描述具体的错误情况
     */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_SERVER_ERROR.getCode();
    }

    /**
     * 构造函数 - 使用自定义状态码和消息创建业务异常
     * 提供最大的灵活性，允许完全自定义状态码和错误消息
     *
     * @param code 自定义状态码，标识特定的错误类型
     * @param message 自定义错误消息，描述具体的错误情况
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
