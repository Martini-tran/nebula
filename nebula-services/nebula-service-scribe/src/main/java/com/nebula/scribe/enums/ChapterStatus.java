package com.nebula.scribe.enums;

import java.util.Arrays;

/**
 * 章节状态，取值与前端 {@code ChapterStatus} 一一对应（库里存小写 code）
 */
public enum ChapterStatus {

    /** 大纲 */
    OUTLINE("outline"),
    /** 草稿 */
    DRAFTING("drafting"),
    /** 修订 */
    REVISING("revising"),
    /** 定稿 */
    DONE("done");

    private final String code;

    ChapterStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(s -> s.code.equals(code));
    }
}
