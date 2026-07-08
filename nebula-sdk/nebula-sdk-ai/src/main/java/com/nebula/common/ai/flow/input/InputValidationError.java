package com.nebula.common.ai.flow.input;

/**
 * 入/出参校验错误项
 * 描述单个字段的一条校验违规：字段键 + 违规类型 + 人类可读消息。校验器聚合成 {@code List} 一次返回，
 * 上层（REST）据此拼装结构化 400 响应。START 入参与 END 出参校验共用本结构。
 *
 * @author nebula
 */
public class InputValidationError {

    /**
     * 违规类型
     */
    public enum Type {
        /** 必填缺失 */
        REQUIRED_MISSING,
        /** 类型不符 */
        TYPE_MISMATCH,
        /** 数值越界（min/max） */
        OUT_OF_RANGE,
        /** 长度越界（minLength/maxLength） */
        LENGTH_OUT_OF_RANGE,
        /** 正则不匹配 */
        PATTERN_MISMATCH,
        /** 不在枚举候选内 */
        NOT_IN_ENUM,
        /** File/Image 元数据非法（大小/格式） */
        FILE_META_INVALID
    }

    private final String key;

    private final Type type;

    private final String message;

    public InputValidationError(String key, Type type, String message) {
        this.key = key;
        this.type = type;
        this.message = message;
    }

    public String getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return key + ": " + message;
    }
}
