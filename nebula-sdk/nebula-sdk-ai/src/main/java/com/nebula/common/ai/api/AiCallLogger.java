package com.nebula.common.ai.api;

import java.util.Map;

/**
 * AI调用日志接口
 *
 * @author nebula
 */
public interface AiCallLogger {

    /**
     * 记录调用日志
     *
     * @param record 日志记录
     */
    void log(Map<String, Object> record);

    /**
     * 记录异常调用日志
     *
     * @param record    日志记录
     * @param throwable 异常信息
     */
    default void logError(Map<String, Object> record, Throwable throwable) {
        log(record);
    }
}
