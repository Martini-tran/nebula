package com.nebula.system.enums;

/**
 * 菜单类型枚举
 * 前端 web-ele/src/views/system/menu 使用字符串值（catalog/menu/button/embedded/link），
 * 数据库 sys_menu.menu_type 用 tinyint 存储，二者通过该枚举互相转换
 *
 * @author nebula
 */
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

    public int getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public static MenuTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MenuTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }

    public static MenuTypeEnum fromType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        for (MenuTypeEnum e : values()) {
            if (e.type.equalsIgnoreCase(type)) {
                return e;
            }
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
