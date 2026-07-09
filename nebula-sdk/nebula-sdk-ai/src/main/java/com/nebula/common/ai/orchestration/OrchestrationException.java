package com.nebula.common.ai.orchestration;

import java.io.Serial;

/**
 * 编排异常
 * 图定义非法（边引用未知节点、存在环）或节点执行失败时抛出。自包含，不依赖 common-core，
 * 与 {@code AiException} 风格一致，保持 sdk-ai 的依赖轻量。
 *
 * @author nebula
 */
public class OrchestrationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OrchestrationException(String message) {
        super(message);
    }

    public OrchestrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
