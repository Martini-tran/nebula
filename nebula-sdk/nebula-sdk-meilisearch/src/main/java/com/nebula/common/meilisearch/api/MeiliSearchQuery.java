package com.nebula.common.meilisearch.api;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 搜索查询请求
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class MeiliSearchQuery {

    /**
     * 查询关键字
     */
    private String q;

    /**
     * 偏移量
     */
    private Integer offset;

    /**
     * 每页数量
     */
    private Integer limit;

    /**
     * 页码（与hitsPerPage配合使用，与offset/limit互斥）
     */
    private Integer page;

    /**
     * 每页命中数（与page配合使用）
     */
    private Integer hitsPerPage;

    /**
     * 过滤条件，例如 status = 1 AND category = 'tech'
     */
    private String filter;

    /**
     * 排序，例如 ["createdAt:desc"]
     */
    private String[] sort;

    /**
     * 需要返回的字段
     */
    private String[] attributesToRetrieve;

    /**
     * 高亮字段
     */
    private String[] attributesToHighlight;

    /**
     * 高亮起始标签
     */
    private String highlightPreTag;

    /**
     * 高亮结束标签
     */
    private String highlightPostTag;

    /**
     * 是否返回匹配位置信息
     */
    private Boolean showMatchesPosition;

    /**
     * 分面字段
     */
    private String[] facets;

    public MeiliSearchQuery() {
    }

    public MeiliSearchQuery(String q) {
        this.q = q;
    }
}
