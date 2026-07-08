package com.nebula.common.ai.flow.input;

/**
 * 开始节点单条入参规格（后端校验用）
 * 对齐前端 {@code start-input.ts} 的 {@code StartInputParam} 校验子集：从 START 节点 {@code nodeConfig.inputs}
 * 解析而来，供 {@link StartInputValidator} 在流程运行前校验外部传入的入参。校验规则字段扁平内联
 * （前端 validation 段的子集），缺省即不约束该维度。
 *
 * @author nebula
 */
public class StartInputSpec {

    /**
     * 入参键（= 上下文键）
     */
    private String key;

    /**
     * 展示名（报错消息用，缺省回退 key）
     */
    private String name;

    /**
     * 类型（String/Number/Boolean/Object/Array/File/Image）
     */
    private String type;

    /**
     * 是否必填
     */
    private boolean required;

    // ---- 校验规则（扁平内联，缺省不约束） ----

    /** String：最小长度 */
    private Integer minLength;

    /** String：最大长度 */
    private Integer maxLength;

    /** String：正则 */
    private String pattern;

    /** Number：最小值 */
    private Double min;

    /** Number：最大值 */
    private Double max;

    /** String/Number：枚举候选（逗号分隔） */
    private String enumValues;

    /** File/Image：大小上限（MB） */
    private Double maxSizeMb;

    /** File/Image：允许格式（逗号分隔，如 png,jpg） */
    private String accept;

    /**
     * 报错用标签：name 优先，缺省 key
     *
     * @return 标签
     */
    public String label() {
        return name != null && !name.isBlank() ? name : key;
    }

    public String getKey() {
        return key;
    }

    public StartInputSpec setKey(String key) {
        this.key = key;
        return this;
    }

    public String getName() {
        return name;
    }

    public StartInputSpec setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return type;
    }

    public StartInputSpec setType(String type) {
        this.type = type;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public StartInputSpec setRequired(boolean required) {
        this.required = required;
        return this;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public StartInputSpec setMinLength(Integer minLength) {
        this.minLength = minLength;
        return this;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public StartInputSpec setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public String getPattern() {
        return pattern;
    }

    public StartInputSpec setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public Double getMin() {
        return min;
    }

    public StartInputSpec setMin(Double min) {
        this.min = min;
        return this;
    }

    public Double getMax() {
        return max;
    }

    public StartInputSpec setMax(Double max) {
        this.max = max;
        return this;
    }

    public String getEnumValues() {
        return enumValues;
    }

    public StartInputSpec setEnumValues(String enumValues) {
        this.enumValues = enumValues;
        return this;
    }

    public Double getMaxSizeMb() {
        return maxSizeMb;
    }

    public StartInputSpec setMaxSizeMb(Double maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
        return this;
    }

    public String getAccept() {
        return accept;
    }

    public StartInputSpec setAccept(String accept) {
        this.accept = accept;
        return this;
    }
}
