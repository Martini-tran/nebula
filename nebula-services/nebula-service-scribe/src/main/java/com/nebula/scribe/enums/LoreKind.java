package com.nebula.scribe.enums;

import java.util.Arrays;

/**
 * 设定条目类型，取值与前端 {@code LoreKind} 一一对应（库里存小写 code）
 */
public enum LoreKind {

    /** 人物 */
    CHARACTER("character"),
    /** 地点 */
    LOCATION("location"),
    /** 势力 */
    FACTION("faction"),
    /** 道具 */
    ITEM("item"),
    /** 世界观规则 */
    RULE("rule");

    private final String code;

    LoreKind(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(k -> k.code.equals(code));
    }
}
