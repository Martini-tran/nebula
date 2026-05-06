package com.nebula.uid.exception;

import java.io.Serial;

/**
 * UID 生成异常，用于包装 UID 生成过程中的各类错误。
 *
 * @author nebula
 */
public class UidGenerateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -27048199131316992L;

    /**
     * 无参构造
     */
    public UidGenerateException() {
        super();
    }

    /**
     * 带消息和原因的构造
     *
     * @param message 异常消息
     * @param cause   异常原因
     */
    public UidGenerateException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 带消息的构造
     *
     * @param message 异常消息
     */
    public UidGenerateException(String message) {
        super(message);
    }

    /**
     * 带格式化消息的构造
     *
     * @param msgFormat 消息格式
     * @param args      格式化参数
     */
    public UidGenerateException(String msgFormat, Object... args) {
        super(String.format(msgFormat, args));
    }

    /**
     * 仅带原因的构造
     *
     * @param cause 异常原因
     */
    public UidGenerateException(Throwable cause) {
        super(cause);
    }
}
