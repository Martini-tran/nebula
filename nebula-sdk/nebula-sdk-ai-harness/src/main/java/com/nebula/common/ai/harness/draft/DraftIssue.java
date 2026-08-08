package com.nebula.common.ai.harness.draft;

/**
 * 可供模型定位并自愈的草稿问题。
 *
 * @param level    ERROR/WARN/INFO
 * @param code     稳定问题编码
 * @param nodeCode 关联节点，可空
 * @param field    关联字段，可空
 * @param message  人类可读说明
 * @param hint     下一步动作建议，可空
 * @author nebula
 */
public record DraftIssue(String level,
                         String code,
                         String nodeCode,
                         String field,
                         String message,
                         String hint) {

    public static DraftIssue error(String code, String nodeCode, String field, String message, String hint) {
        return new DraftIssue("ERROR", code, nodeCode, field, message, hint);
    }

    public static DraftIssue warn(String code, String nodeCode, String field, String message, String hint) {
        return new DraftIssue("WARN", code, nodeCode, field, message, hint);
    }
}
