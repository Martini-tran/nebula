package com.nebula.forge.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件商城前台分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginFrontPageQuery extends PageQuery {

    /**
     * 关键词（插件标识、名称或简介）
     */
    private String keyword;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 插件类型：inline/view
     */
    private String type;

    /**
     * 定价类型：1免费 2付费 3订阅 4外部购买
     */
    private Integer pricingType;

    /**
     * 排序方式：featured(推荐，默认) / new(最新) / hot(下载最多) / rating(评分最高)
     */
    private String sort;
}
