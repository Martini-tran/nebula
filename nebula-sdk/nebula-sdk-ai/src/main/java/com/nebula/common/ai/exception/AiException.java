package com.nebula.common.ai.exception;

import java.io.Serial;

/**
 * AI调用异常
 * sdk-ai 内自包含的运行时异常，避免为抛错引入业务核心模块依赖。
 *
 * @author nebula
 */
public class AiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
