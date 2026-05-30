package com.nebula.blog.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 中转推荐分页查询参数（前台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayRecommendFrontPageQuery extends PageQuery {

    /**
     * 服务商ID（可选，按服务商筛选）
     */
    private Long providerId;

    /**
     * 关键词（推荐原因/测评/使用场景）
     */
    private String keyword;

    /**
     * 排序方式：score(默认按评分倒序) / time(按推荐时间倒序) / sort(按 sort_order 升序)
     */
    private String sortBy;
}
