package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 目标新建 / 局部保存请求
 *
 * <p>区分「没传」和「传了 null」（改成手动来源时 sourceId: null），setter 记下请求里出现的字段。
 * 页面上 +1、勾关键结果都只传那一个字段。</p>
 */
@Getter
public class GoalSaveRequest {

    @JsonIgnore
    private final Set<String> present = new HashSet<>();

    @Min(2000)
    @Max(2100)
    private Integer year;

    @Size(max = 100, message = "目标最长 100 字")
    private String title;

    /**
     * Iconify 名称（集合:名字），如 lucide:target
     */
    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9-]+:[a-z0-9-]+", message = "图标应为 Iconify 名称，如 lucide:target")
    private String icon;

    @Pattern(regexp = "metric|milestone", message = "不支持的目标类型")
    private String kind;

    @DecimalMin("0")
    @DecimalMax("999999999999")
    private BigDecimal target;

    @Size(max = 16, message = "单位最长 16 字")
    private String unit;

    @Pattern(regexp = "habit|reading|ledger|task_list|manual", message = "不支持的进度来源")
    private String source;

    private Long sourceId;

    @DecimalMin(value = "0", inclusive = false, message = "折算必须大于 0")
    @DecimalMax("99999999")
    private BigDecimal factor;

    @DecimalMin("-999999999999")
    @DecimalMax("999999999999")
    private BigDecimal baseline;

    @DecimalMin("0")
    @DecimalMax("999999999999")
    private BigDecimal manualValue;

    @Valid
    @Size(max = 50, message = "关键结果最多 50 条")
    private List<GoalKeyResult> krs;

    /**
     * 请求里是否带了这个字段（值可以是 null）
     */
    public boolean has(String field) {
        return present.contains(field);
    }

    public void setYear(Integer year) {
        this.year = year;
        present.add("year");
    }

    public void setTitle(String title) {
        this.title = title;
        present.add("title");
    }

    public void setIcon(String icon) {
        this.icon = icon;
        present.add("icon");
    }

    public void setKind(String kind) {
        this.kind = kind;
        present.add("kind");
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
        present.add("target");
    }

    public void setUnit(String unit) {
        this.unit = unit;
        present.add("unit");
    }

    public void setSource(String source) {
        this.source = source;
        present.add("source");
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
        present.add("sourceId");
    }

    public void setFactor(BigDecimal factor) {
        this.factor = factor;
        present.add("factor");
    }

    public void setBaseline(BigDecimal baseline) {
        this.baseline = baseline;
        present.add("baseline");
    }

    public void setManualValue(BigDecimal manualValue) {
        this.manualValue = manualValue;
        present.add("manualValue");
    }

    public void setKrs(List<GoalKeyResult> krs) {
        this.krs = krs;
        present.add("krs");
    }
}
