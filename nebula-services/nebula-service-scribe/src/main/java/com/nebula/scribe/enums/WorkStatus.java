package com.nebula.scribe.enums;

import java.util.Arrays;

/**
 * 作品状态，取值与前端 {@code WorkStatus} 一一对应（库里存小写 code）
 */
public enum WorkStatus {

    /** 构思中 */
    DRAFT("draft"),
    /** 连载中 */
    SERIALIZING("serializing"),
    /** 暂停 */
    PAUSED("paused"),
    /** 已完结 */
    FINISHED("finished");

    private final String code;

    WorkStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(s -> s.code.equals(code));
    }
}
