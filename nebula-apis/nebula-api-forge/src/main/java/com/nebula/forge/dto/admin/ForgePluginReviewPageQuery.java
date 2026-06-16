package com.nebula.forge.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件评价分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginReviewPageQuery extends PageQuery {

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 评价用户ID
     */
    private Long userId;

    /**
     * 状态：0待审 1展示 2隐藏 3拒绝
     */
    private Integer status;

    /**
     * 评分：1-5
     */
    private Integer rating;
}
