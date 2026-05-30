package com.nebula.blog.dto.front;

import lombok.Data;

/**
 * 前端系列分页查询参数
 */
@Data
public class SeriesPageQuery {

    /**
     * 关键词，匹配 name / description
     */
    private String keyword;

    /**
     * 是否完结：true 已完结 / false 连载中 / null 全部
     */
    private Boolean isFinished;

    /**
     * 游标（上一页最后一项的 sort_order:id）
     */
    private String cursor;

    /**
     * 每页数量，默认 12
     */
    private Integer limit = 12;
}
