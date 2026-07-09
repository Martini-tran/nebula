package com.nebula.common.ai.flow.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结束节点出参校验器
 * 校验 END 渲染出的最终 JSON 对象是否符合声明的 {@code outputSchema}：必出字段缺失、类型不符。
 * 复用 {@link InputValidationError} 结构与 {@link TypeMatcher} 类型判定，与 START 入参校验语义一致。
 *
 * <p><b>非阻断</b>：本校验结果由 {@code EndNodeExecutor} 以告警形式记录，不翻盘流程终态——「END 到达即成功」
 * 是既有强契约（且是记忆导出的前提），schema 不符只提示、不阻断。未声明 schema 的流程返回空列表（不校验）。
 *
 * @author nebula
 */
public class EndOutputValidator {

    /**
     * 从 {@code nodeConfig.end.outputSchema} 解析出参规格列表。
     *
     * @param raw outputSchema 原始值（期望 List&lt;Map&gt;）
     * @return 规格列表；非法/缺失返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<EndOutputSpec> parse(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<EndOutputSpec> specs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) m;
            String name = str(row.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            specs.add(new EndOutputSpec()
                    .setName(name.trim())
                    .setType(str(row.get("type")))
                    .setRequired(bool(row.get("required"))));
        }
        return specs;
    }

    /**
     * 校验渲染结果。返回空列表表示通过。
     *
     * @param schema   出参规格列表（空则不校验）
     * @param rendered END 渲染出的最终 JSON 对象
     * @return 违规列表（聚合全部）
     */
    public List<InputValidationError> validate(List<EndOutputSpec> schema, Map<String, Object> rendered) {
        List<InputValidationError> errors = new ArrayList<>();
        if (schema == null || schema.isEmpty()) {
            return errors;
        }
        Map<String, Object> out = rendered == null ? Map.of() : rendered;
        for (EndOutputSpec spec : schema) {
            Object value = out.get(spec.getName());
            boolean missing = value == null || (value instanceof CharSequence cs && cs.toString().isBlank());
            if (missing) {
                if (spec.isRequired()) {
                    errors.add(new InputValidationError(spec.getName(),
                            InputValidationError.Type.REQUIRED_MISSING,
                            "END 缺少必出字段「" + spec.getName() + "」"));
                }
                continue;
            }
            if (!TypeMatcher.matches(value, spec.getType())) {
                errors.add(new InputValidationError(spec.getName(),
                        InputValidationError.Type.TYPE_MISMATCH,
                        "END 字段「" + spec.getName() + "」类型应为 " + spec.getType()));
            }
        }
        return errors;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private boolean bool(Object v) {
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }
}
