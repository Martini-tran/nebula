package com.nebula.common.ai.flow.input;

/**
 * 结束节点单条出参规格
 * 对齐 START 入参的类型体系，描述 END 渲染出的最终 JSON 对象应含的一个字段：字段名 + 类型 + 是否必出。
 * 从 END 节点 {@code nodeConfig.end.outputSchema} 解析而来，供 {@code EndOutputValidator} 校验渲染结果。
 * <b>可选</b>：未声明 outputSchema 的流程不受任何约束（向后兼容）。
 *
 * @author nebula
 */
public class EndOutputSpec {

    /**
     * 出参字段名（= 最终 JSON 的顶层键）
     */
    private String name;

    /**
     * 类型（String/Number/Boolean/Object/Array）
     */
    private String type;

    /**
     * 是否必出（缺失则告警）
     */
    private boolean required;

    public String getName() {
        return name;
    }

    public EndOutputSpec setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return type;
    }

    public EndOutputSpec setType(String type) {
        this.type = type;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public EndOutputSpec setRequired(boolean required) {
        this.required = required;
        return this;
    }
}
