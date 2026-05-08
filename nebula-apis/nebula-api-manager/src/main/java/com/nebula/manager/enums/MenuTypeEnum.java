package com.nebula.manager.enums;

public enum MenuTypeEnum {

    CATALOG(1, "catalog"),
    MENU(2, "menu"),
    BUTTON(3, "button"),
    EMBEDDED(4, "embedded"),
    LINK(5, "link");

    private final int code;
    private final String type;

    MenuTypeEnum(int code, String type) {
        this.code = code;
        this.type = type;
    }

    public int getCode() { return code; }
    public String getType() { return type; }

    public static MenuTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (MenuTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    public static MenuTypeEnum fromType(String type) {
        if (type == null || type.isBlank()) return null;
        for (MenuTypeEnum e : values()) {
            if (e.type.equalsIgnoreCase(type)) return e;
        }
        return null;
    }

    public static String codeToType(Integer code) {
        MenuTypeEnum e = fromCode(code);
        return e == null ? null : e.type;
    }

    public static Integer typeToCode(String type) {
        MenuTypeEnum e = fromType(type);
        return e == null ? null : e.code;
    }
}
