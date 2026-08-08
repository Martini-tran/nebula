package com.nebula.common.ai.harness.realrun;

/** Harness 高风险操作状态。 */
public enum HarnessOperationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    UNKNOWN;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == UNKNOWN;
    }
}
