package com.nebula.common.ai.harness.realrun;

/** 服务端确认生命周期。 */
public enum ConfirmationStatus {
    PENDING,
    CONFIRMED,
    CONSUMED,
    EXPIRED,
    CANCELLED
}
