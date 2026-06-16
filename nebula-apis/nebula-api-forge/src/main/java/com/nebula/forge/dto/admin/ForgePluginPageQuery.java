package com.nebula.forge.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginPageQuery extends PageQuery {

    /**
     * 关键词（插件标识、名称或简介）
     */
    private String keyword;

    /**
     * 状态：0草稿 1上架 2下架 3封禁
     */
    private Integer status;

    /**
     * 插件类型：inline/view
     */
    private String type;

    /**
     * 定价类型：1免费 2付费 3订阅 4外部购买
     */
    private Integer pricingType;

    /**
     * 是否推荐：1是 0否
     */
    private Integer isFeatured;

    /**
     * 分类ID（按关联分类过滤）
     */
    private Long categoryId;
}
