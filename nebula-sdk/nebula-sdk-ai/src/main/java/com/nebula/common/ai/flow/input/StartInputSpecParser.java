package com.nebula.common.ai.flow.input;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.NoOpNodeExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 开始节点入参规格解析器
 * 从 {@link FlowDefinition} 的 START 节点 {@code nodeConfig.inputs} 解析出 {@link StartInputSpec} 列表，
 * 对齐前端 {@code normalizeStartInputs} 的两种历史形态：
 * <ul>
 *   <li><b>数组形态</b>：{@code [{key,name,type,required,validation:{...}}]}，validation 段扁平化进 spec；</li>
 *   <li><b>对象形态</b>：{@code {key: defaultValue}}，按值推断 type、无 required/validation。</li>
 * </ul>
 * 无 START 节点 / 无 inputs → 空列表（老流程或无入参流程跳过校验）。
 *
 * @author nebula
 */
public class StartInputSpecParser {

    /**
     * 从流程定义解析 START 入参规格列表。
     *
     * @param def 流程定义
     * @return 入参规格列表（无入参返回空列表）
     */
    @SuppressWarnings("unchecked")
    public List<StartInputSpec> parse(FlowDefinition def) {
        if (def == null || def.getNodes() == null) {
            return List.of();
        }
        FlowNodeDefinition start = def.getNodes().stream()
                .filter(n -> NoOpNodeExecutor.TYPE_START.equalsIgnoreCase(n.getNodeType()))
                .findFirst()
                .orElse(null);
        if (start == null || start.getNodeConfig() == null) {
            return List.of();
        }
        Object inputs = start.getNodeConfig().get("inputs");
        if (inputs instanceof List<?> list) {
            return parseArray(list);
        }
        if (inputs instanceof Map<?, ?> map) {
            return parseObject((Map<String, Object>) map);
        }
        return List.of();
    }

    /**
     * 数组形态：逐项映射，validation 段扁平化。
     */
    @SuppressWarnings("unchecked")
    private List<StartInputSpec> parseArray(List<?> list) {
        List<StartInputSpec> specs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) m;
            String key = str(row.get("key"));
            if (key == null || key.isBlank()) {
                key = str(row.get("name")); // 容忍仅有 name 的历史行
            }
            if (key == null || key.isBlank()) {
                continue;
            }
            StartInputSpec spec = new StartInputSpec()
                    .setKey(key.trim())
                    .setName(str(row.get("name")))
                    .setType(str(row.get("type")))
                    .setRequired(bool(row.get("required")));
            applyValidation(spec, row.get("validation"));
            specs.add(spec);
        }
        return specs;
    }

    /**
     * 对象形态：{key: defaultValue}，按值推断 type，无 required/validation。
     */
    private List<StartInputSpec> parseObject(Map<String, Object> obj) {
        List<StartInputSpec> specs = new ArrayList<>();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            specs.add(new StartInputSpec()
                    .setKey(e.getKey())
                    .setName(e.getKey())
                    .setType(inferType(e.getValue()))
                    .setRequired(false));
        }
        return specs;
    }

    /**
     * 扁平化 validation 子对象进 spec。
     */
    @SuppressWarnings("unchecked")
    private void applyValidation(StartInputSpec spec, Object validation) {
        if (!(validation instanceof Map<?, ?> m)) {
            return;
        }
        Map<String, Object> v = (Map<String, Object>) m;
        spec.setMinLength(intOrNull(v.get("minLength")));
        spec.setMaxLength(intOrNull(v.get("maxLength")));
        spec.setPattern(str(v.get("pattern")));
        spec.setMin(doubleOrNull(v.get("min")));
        spec.setMax(doubleOrNull(v.get("max")));
        spec.setEnumValues(str(v.get("enumValues")));
        spec.setMaxSizeMb(doubleOrNull(v.get("maxSizeMb")));
        spec.setAccept(str(v.get("accept")));
    }

    /**
     * 按值推断类型（对齐前端 inferStartInputType）
     */
    private String inferType(Object value) {
        if (value instanceof Number) {
            return "Number";
        }
        if (value instanceof Boolean) {
            return "Boolean";
        }
        if (value instanceof List<?> || value instanceof Object[]) {
            return "Array";
        }
        if (value instanceof Map<?, ?>) {
            return "Object";
        }
        return "String";
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private boolean bool(Object v) {
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private Integer intOrNull(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null || String.valueOf(v).isBlank() ? null : Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double doubleOrNull(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null || String.valueOf(v).isBlank() ? null : Double.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
