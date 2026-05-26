package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;
import org.springframework.context.ApplicationEvent;

/**
 * AI调用前事件
 *
 * @author nebula
 */
public class AiBeforeCallEvent extends ApplicationEvent {

    /**
     * 请求参数
     */
    private final AiRequest request;

    public AiBeforeCallEvent(Object source, AiRequest request) {
        super(source);
        this.request = request;
    }

    /**
     * 获取请求参数
     *
     * @return 请求参数
     */
    public AiRequest getRequest() {
        return request;
    }
}
