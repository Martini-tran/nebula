package com.nebula.scribe.enums;

import java.util.Arrays;

/**
 * 目标读者（对应网文平台的男频/女频）
 */
public enum WorkAudience {

    /** 男频 */
    MALE("male"),
    /** 女频 */
    FEMALE("female"),
    /** 不限 */
    GENERAL("general");

    private final String code;

    WorkAudience(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(a -> a.code.equals(code));
    }
}
