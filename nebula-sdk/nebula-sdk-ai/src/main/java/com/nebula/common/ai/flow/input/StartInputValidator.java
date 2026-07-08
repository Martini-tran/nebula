package com.nebula.common.ai.flow.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 开始节点入参校验器
 * 对外部传入的入参 Map 按 {@link StartInputSpec} 列表校验，与前端 {@code RunInputForm.collect()} 同规则
 * （required / 类型 / min-max / minLength-maxLength / pattern / enumValues / File-Image 元数据），差异是
 * <b>聚合全部违规一次返回，不短路</b>——一次把所有错误报给用户，而非逐条试错。
 *
 * <p>后端补此校验以堵住前端表单之外的入口（REST 直调、子 Agent inputs、cron）：前端校验能被绕过，
 * 后端才是唯一可信防线。类型判定走宽松 {@link TypeMatcher}（容忍字符串化数字/布尔）。
 *
 * @author nebula
 */
public class StartInputValidator {

    /**
     * 校验入参。返回空列表表示全部通过。
     *
     * @param specs 入参规格列表（空则不校验，直接通过）
     * @param input 外部传入的入参
     * @return 违规列表（聚合全部，不短路）
     */
    public List<InputValidationError> validate(List<StartInputSpec> specs, Map<String, Object> input) {
        List<InputValidationError> errors = new ArrayList<>();
        if (specs == null || specs.isEmpty()) {
            return errors;
        }
        Map<String, Object> in = input == null ? Map.of() : input;
        for (StartInputSpec spec : specs) {
            validateOne(spec, in, errors);
        }
        return errors;
    }

    private void validateOne(StartInputSpec spec, Map<String, Object> input, List<InputValidationError> errors) {
        String key = spec.getKey();
        String label = spec.label();
        Object value = input.get(key);

        boolean missing = value == null || (value instanceof CharSequence cs && cs.toString().isBlank());
        if (missing) {
            if (spec.isRequired()) {
                errors.add(new InputValidationError(key, InputValidationError.Type.REQUIRED_MISSING,
                        "请填写「" + label + "」"));
            }
            return; // 非必填缺省：跳过后续规则（无值可校）
        }

        // 类型（宽松）：不符直接记 TYPE_MISMATCH 并跳过依赖类型的后续规则
        if (!TypeMatcher.matches(value, spec.getType())) {
            errors.add(new InputValidationError(key, InputValidationError.Type.TYPE_MISMATCH,
                    "「" + label + "」类型应为 " + spec.getType()));
            return;
        }

        String type = spec.getType() == null ? "String" : spec.getType().trim();
        switch (type.toLowerCase()) {
            case "number" -> validateNumber(spec, key, label, value, errors);
            case "file", "image" -> validateFile(spec, key, label, value, errors);
            case "string" -> validateString(spec, key, label, value, errors);
            default -> {
                // Boolean/Object/Array：类型已过，无额外规则
            }
        }
    }

    private void validateString(StartInputSpec spec, String key, String label, Object value,
                                List<InputValidationError> errors) {
        String s = String.valueOf(value);
        if (spec.getMinLength() != null && s.length() < spec.getMinLength()) {
            errors.add(new InputValidationError(key, InputValidationError.Type.LENGTH_OUT_OF_RANGE,
                    "「" + label + "」长度不能小于 " + spec.getMinLength()));
        }
        if (spec.getMaxLength() != null && s.length() > spec.getMaxLength()) {
            errors.add(new InputValidationError(key, InputValidationError.Type.LENGTH_OUT_OF_RANGE,
                    "「" + label + "」长度不能大于 " + spec.getMaxLength()));
        }
        if (spec.getPattern() != null && !spec.getPattern().isBlank()) {
            try {
                if (!Pattern.compile(spec.getPattern()).matcher(s).find()) {
                    errors.add(new InputValidationError(key, InputValidationError.Type.PATTERN_MISMATCH,
                            "「" + label + "」不符合格式要求（" + spec.getPattern() + "）"));
                }
            } catch (PatternSyntaxException e) {
                // 无效正则视为未配置，不拦截运行（与前端一致）
            }
        }
        List<String> candidates = enumList(spec.getEnumValues());
        if (!candidates.isEmpty() && !candidates.contains(s)) {
            errors.add(new InputValidationError(key, InputValidationError.Type.NOT_IN_ENUM,
                    "「" + label + "」必须是候选值之一：" + String.join("、", candidates)));
        }
    }

    private void validateNumber(StartInputSpec spec, String key, String label, Object value,
                                List<InputValidationError> errors) {
        Double n = TypeMatcher.asNumber(value);
        if (n == null) {
            return; // 已由 matches 拦截，防御
        }
        if (spec.getMin() != null && n < spec.getMin()) {
            errors.add(new InputValidationError(key, InputValidationError.Type.OUT_OF_RANGE,
                    "「" + label + "」不能小于 " + trim(spec.getMin())));
        }
        if (spec.getMax() != null && n > spec.getMax()) {
            errors.add(new InputValidationError(key, InputValidationError.Type.OUT_OF_RANGE,
                    "「" + label + "」不能大于 " + trim(spec.getMax())));
        }
        List<String> candidates = enumList(spec.getEnumValues());
        if (!candidates.isEmpty()) {
            boolean hit = candidates.stream()
                    .map(c -> {
                        try {
                            return Double.valueOf(c.trim());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .anyMatch(d -> d != null && d.equals(n));
            if (!hit) {
                errors.add(new InputValidationError(key, InputValidationError.Type.NOT_IN_ENUM,
                        "「" + label + "」必须是候选值之一：" + String.join("、", candidates)));
            }
        }
    }

    /**
     * File/Image：只校元数据（值为 {name,sizeMb,ext/contentType,url} Map 或纯 URL 字符串）。
     * 取不到元数据（如纯 URL）时只保证有值——required 已在上游判过，此处不误判。
     */
    @SuppressWarnings("unchecked")
    private void validateFile(StartInputSpec spec, String key, String label, Object value,
                              List<InputValidationError> errors) {
        if (!(value instanceof Map<?, ?> m)) {
            return; // 纯 URL 字符串等：无元数据可校，放行
        }
        Map<String, Object> meta = (Map<String, Object>) m;
        Double sizeMb = TypeMatcher.asNumber(meta.get("sizeMb"));
        if (spec.getMaxSizeMb() != null && sizeMb != null && sizeMb > spec.getMaxSizeMb()) {
            errors.add(new InputValidationError(key, InputValidationError.Type.FILE_META_INVALID,
                    "「" + label + "」大小超过上限 " + trim(spec.getMaxSizeMb()) + "MB"));
        }
        List<String> accepts = enumList(spec.getAccept());
        if (!accepts.isEmpty()) {
            String ext = extOf(meta);
            if (ext != null && accepts.stream().noneMatch(a -> a.equalsIgnoreCase(ext))) {
                errors.add(new InputValidationError(key, InputValidationError.Type.FILE_META_INVALID,
                        "「" + label + "」格式必须是：" + String.join("、", accepts)));
            }
        }
    }

    /**
     * 从元数据取扩展名：优先 ext，退化 name/url 后缀，再退化 contentType 后段。
     */
    private String extOf(Map<String, Object> meta) {
        String ext = str(meta.get("ext"));
        if (ext != null && !ext.isBlank()) {
            return ext.replace(".", "").trim();
        }
        String name = str(meta.get("name"));
        if (name == null) {
            name = str(meta.get("url"));
        }
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.') + 1).trim();
        }
        String ct = str(meta.get("contentType"));
        if (ct != null && ct.contains("/")) {
            return ct.substring(ct.lastIndexOf('/') + 1).trim();
        }
        return null;
    }

    private List<String> enumList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(raw.split(","))) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 去掉整数值的 .0 尾巴，报错更自然 */
    private String trim(Double d) {
        if (d == null) {
            return "";
        }
        if (d == Math.floor(d) && !d.isInfinite()) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(d);
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
