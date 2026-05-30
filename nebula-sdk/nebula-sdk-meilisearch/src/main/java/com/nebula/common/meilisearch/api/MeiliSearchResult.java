package com.nebula.common.meilisearch.api;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 搜索结果
 *
 * @param <T> 文档类型
 * @author nebula
 */
@Data
public class MeiliSearchResult<T> {

    /**
     * 命中的文档列表
     */
    private List<T> hits;

    /**
     * 查询关键字
     */
    private String query;

    /**
     * 查询耗时（ms）
     */
    private Integer processingTimeMs;

    /**
     * 偏移量（offset/limit 模式）
     */
    private Integer offset;

    /**
     * 每页数量（offset/limit 模式）
     */
    private Integer limit;

    /**
     * 估计的命中总数（offset/limit 模式）
     */
    private Integer estimatedTotalHits;

    /**
     * 当前页码（page/hitsPerPage 模式）
     */
    private Integer page;

    /**
     * 每页命中数（page/hitsPerPage 模式）
     */
    private Integer hitsPerPage;

    /**
     * 总命中数（page/hitsPerPage 模式）
     */
    private Integer totalHits;

    /**
     * 总页数（page/hitsPerPage 模式）
     */
    private Integer totalPages;

    /**
     * 分面分布
     */
    private Map<String, Map<String, Integer>> facetDistribution;
}
