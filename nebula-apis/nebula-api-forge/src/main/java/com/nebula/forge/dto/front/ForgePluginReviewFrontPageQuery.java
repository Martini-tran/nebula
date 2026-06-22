package com.nebula.forge.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件评价前台分页查询
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginReviewFrontPageQuery extends PageQuery {

    /**
     * 排序方式：new(最新，默认) / rating(评分最高) / like(点赞最多)
     */
    private String sort;
}
