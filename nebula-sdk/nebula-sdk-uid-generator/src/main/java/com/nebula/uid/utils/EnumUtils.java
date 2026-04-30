package com.nebula.uid.utils;

import org.springframework.util.Assert;

/**
 * 枚举工具类，提供 {@link ValuedEnum} 的解析操作。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
public abstract class EnumUtils {

    /**
     * 根据值解析为对应的 {@link ValuedEnum} 枚举常量
     *
     * @param clz   枚举类
     * @param value 枚举值
     * @return 匹配的枚举常量；若无匹配返回 null
     */
    public static <T extends ValuedEnum<V>, V> T parse(Class<T> clz, V value) {
        Assert.notNull(clz, "clz can not be null");
        if (value == null) {
            return null;
        }

        for (T t : clz.getEnumConstants()) {
            if (value.equals(t.value())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 安全的 valueOf 方法（name 为 null 时返回 null 而非抛异常）
     */
    public static <T extends Enum<T>> T valueOf(Class<T> enumType, String name) {
        if (name == null) {
            return null;
        }
        return Enum.valueOf(enumType, name);
    }
}
