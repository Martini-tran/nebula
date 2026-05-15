package com.nebula.manager.enums;

/**
 * 菜单类型枚举
 */
public enum MenuTypeEnum {

    /** 目录 */
    CATALOG(1, "catalog"),
    /** 菜单 */
    MENU(2, "menu"),
    /** 按钮 */
    BUTTON(3, "button"),
    /** 内嵌页面 */
    EMBEDDED(4, "embedded"),
    /** 外链 */
    LINK(5, "link");

    /** 类型编码 */
    private final int code;
    /** 类型标识 */
    private final String type;

    MenuTypeEnum(int code, String type) {
        this.code = code;
        this.type = type;
    }

    public int getCode() { return code; }
    public String getType() { return type; }

    /**
     * 根据编码获取枚举
     */
    public static MenuTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (MenuTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    /**
     * 根据类型标识获取枚举
     */
    public static MenuTypeEnum fromType(String type) {
        if (type == null || type.isBlank()) return null;
        for (MenuTypeEnum e : values()) {
            if (e.type.equalsIgnoreCase(type)) return e;
        }
        return null;
    }

    /**
     * 编码转类型标识
     */
    public static String codeToType(Integer code) {
        MenuTypeEnum e = fromCode(code);
        return e == null ? null : e.type;
    }

    /**
     * 类型标识转编码
     */
    public static Integer typeToCode(String type) {
        MenuTypeEnum e = fromType(type);
        return e == null ? null : e.code;
    }
}
