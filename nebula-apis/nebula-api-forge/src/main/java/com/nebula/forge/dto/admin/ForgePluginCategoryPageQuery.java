package com.nebula.forge.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件分类分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginCategoryPageQuery extends PageQuery {

    /**
     * 关键词（分类编码或名称）
     */
    private String keyword;

    /**
     * 状态：1启用 0停用
     */
    private Integer status;
}
