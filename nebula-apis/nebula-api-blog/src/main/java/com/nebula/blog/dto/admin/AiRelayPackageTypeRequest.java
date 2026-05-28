package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * AI中转套餐类型 创建/更新请求
 */
@Data
public class AiRelayPackageTypeRequest {

    /**
     * 套餐类型编码（必填，最长 50）
     */
    private String code;

    /**
     * 套餐类型名称（必填，最长 50）
     */
    private String name;

    /**
     * 计费模式（必填，1固定周期 2按量计费）
     */
    private Integer billingMode;

    private Integer durationValue;

    /**
     * 周期单位（1天 2周 3月 4年）
     */
    private Integer durationUnit;

    /**
     * 类型说明（最长 500）
     */
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
