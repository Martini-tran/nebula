package com.nebula.common.ai.harness.runtime;

/**
 * Harness 事件出口。
 *
 * <p>实现必须自行处理客户端断连等传输异常，避免事件发送失败反向破坏工具执行状态。
 *
 * @author nebula
 */
public interface HarnessEventSink {

    void publish(HarnessEvent event);

    /**
     * @return 下游是否已终止；Harness 据此尽快停止后续模型/工具调用
     */
    default boolean isTerminated() {
        return false;
    }
}
