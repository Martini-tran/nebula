package com.nebula.common.ai.flow.input;

import java.util.Collection;
import java.util.Map;

/**
 * 入/出参类型宽松匹配器
 * 判定一个运行期值是否「符合」声明类型（对齐前端 {@code StartInputType}：String/Number/Boolean/Object/
 * Array/File/Image）。之所以「宽松」：前端入参 {@code defaultValue} 与运行表单值常是字符串化的数字/布尔，
 * END 渲染后的值又是 JSON 反序列化类型（Integer/Double/Boolean/List/Map）——若严格按 Java 类型判定会误报。
 * 故 Number 容忍可解析为数字的字符串、Boolean 容忍 "true"/"false"/"1"/"0"。
 *
 * <p>START 入参校验（{@link StartInputValidator}）与 END 出参校验（EndOutputValidator）共用本类，
 * 保证两侧类型语义一致。File/Image 只做「有值即通过」的元数据存在性判定，字节级校验不在此。
 *
 * @author nebula
 */
public final class TypeMatcher {

    private TypeMatcher() {
    }

    /**
     * 值是否符合声明类型（宽松）。type 为空/未知一律放行（不误拦未声明类型）。
     *
     * @param value 运行期值（非 null——null 的必填判定由调用方先做）
     * @param type  声明类型（String/Number/Boolean/Object/Array/File/Image，大小写不敏感）
     * @return 是否匹配
     */
    public static boolean matches(Object value, String type) {
        if (value == null || type == null || type.isBlank()) {
            return true;
        }
        return switch (type.trim().toLowerCase()) {
            case "number" -> asNumber(value) != null;
            case "boolean" -> asBoolean(value) != null;
            case "array" -> value instanceof Collection<?> || value instanceof Object[];
            case "object" -> value instanceof Map<?, ?>;
            // String/File/Image：有值即视为通过（File/Image 后续按 validation 校元数据）
            default -> true;
        };
    }

    /**
     * 尝试把值解析为数字（Number 原样；可解析的字符串转 Double）；失败返回 null。
     *
     * @param value 值
     * @return 数值，或 null
     */
    public static Double asNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 尝试把值解析为布尔（Boolean 原样；"true"/"false"/"1"/"0" 字符串可解析）；失败返回 null。
     *
     * @param value 值
     * @return 布尔值，或 null（无法判定）
     */
    public static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        return switch (s) {
            case "true", "1" -> Boolean.TRUE;
            case "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
