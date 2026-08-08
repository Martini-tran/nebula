package com.nebula.common.ai.harness.realrun;

/** 原子消费确认并创建 operation 的结果。 */
public record OperationAuthorization(boolean authorized, HarnessOperation operation) {

    public static OperationAuthorization rejected() {
        return new OperationAuthorization(false, null);
    }

    public static OperationAuthorization authorized(HarnessOperation operation) {
        return new OperationAuthorization(true, operation);
    }
}
