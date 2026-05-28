package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI中转套餐类型 创建/更新请求
 */
@Data
public class AiRelayPackageTypeRequest {

    @NotBlank(message = "套餐类型编码不能为空")
    @Size(max = 50, message = "套餐类型编码长度不能超过50")
    private String code;

    @NotBlank(message = "套餐类型名称不能为空")
    @Size(max = 50, message = "套餐类型名称长度不能超过50")
    private String name;

    /**
     * 计费模式（1固定周期 2按量计费）
     */
    @NotNull(message = "计费模式不能为空")
    private Integer billingMode;

    private Integer durationValue;

    /**
     * 周期单位（1天 2周 3月 4年）
     */
    private Integer durationUnit;

    @Size(max = 500, message = "类型说明长度不能超过500")
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
