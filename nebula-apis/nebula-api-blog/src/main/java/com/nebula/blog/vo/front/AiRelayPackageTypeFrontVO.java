package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 中转套餐类型字典 VO（前台）
 */
@Data
public class AiRelayPackageTypeFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    /**
     * 计费模式：usage / subscription
     */
    private String billingMode;

    /**
     * 时长数值（如 30）
     */
    private Integer durationValue;

    /**
     * 时长单位（1=天 2=周 3=月 ...）
     */
    private Integer durationUnit;

    private String description;
}
