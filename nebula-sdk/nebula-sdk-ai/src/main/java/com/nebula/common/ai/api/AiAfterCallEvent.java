package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * AI调用后事件
 *
 * @author nebula
 */
public class AiAfterCallEvent extends ApplicationEvent {

    /**
     * 请求参数
     */
    private final AiRequest request;

    /**
     * 响应结果
     */
    private final Map<String, Object> response;

    /**
     * 异常信息
     */
    private final Throwable throwable;

    /**
     * 调用耗时（毫秒）
     */
    private final Long latencyMs;

    public AiAfterCallEvent(Object source,
                            AiRequest request,
                            Map<String, Object> response,
                            Throwable throwable,
                            Long latencyMs) {
        super(source);
        this.request = request;
        this.response = response;
        this.throwable = throwable;
        this.latencyMs = latencyMs;
    }

    /**
     * 获取请求参数
     *
     * @return 请求参数
     */
    public AiRequest getRequest() {
        return request;
    }

    /**
     * 获取响应结果
     *
     * @return 响应结果
     */
    public Map<String, Object> getResponse() {
        return response;
    }

    /**
     * 获取异常信息
     *
     * @return 异常信息
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * 获取调用耗时
     *
     * @return 调用耗时（毫秒）
     */
    public Long getLatencyMs() {
        return latencyMs;
    }

    /**
     * 判断调用是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return throwable == null;
    }
}
