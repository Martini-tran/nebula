package com.nebula.common.ai.orchestration;

/**
 * 编排执行实例状态
 * 描述一次 flow 编排执行的生命周期：进行中 → 成功/失败。失败实例保留已落库进度，可经 runId 续跑。
 *
 * @author nebula
 */
public enum RunStatus {

    /**
     * 进行中：已创建执行实例，尚未结算
     */
    RUNNING,

    /**
     * 成功：全部可达节点执行完成
     */
    SUCCESS,

    /**
     * 失败：某节点执行抛异常，进度已保留，可从断点续跑
     */
    FAILED
}
