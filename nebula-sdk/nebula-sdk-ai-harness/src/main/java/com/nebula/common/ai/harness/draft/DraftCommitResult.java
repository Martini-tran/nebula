package com.nebula.common.ai.harness.draft;

/** 宿主提交适配器的稳定结果契约。 */
public record DraftCommitResult(boolean committed,
                                String flowCode,
                                Integer version,
                                String errorCode,
                                String message,
                                String hint) {

    public static DraftCommitResult success(String flowCode, int version) {
        return new DraftCommitResult(true, flowCode, version, null, null, null);
    }

    public static DraftCommitResult failure(String errorCode, String message, String hint) {
        return new DraftCommitResult(false, null, null, errorCode, message, hint);
    }
}
