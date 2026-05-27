package com.nebula.blog.dto.front;

import lombok.Data;

/**
 * 游记前台分页查询DTO
 */
@Data
public class TravelTripPageQuery {

    /**
     * 关键词
     */
    private String keyword;

    /**
     * 目的地ID（按打卡点的目的地过滤）
     */
    private Long destinationId;

    /**
     * 游标（上一页最后一条 publishedAt 的 epoch 秒数）
     */
    private String cursor;

    /**
     * 每页数量，默认10
     */
    private Integer limit = 10;
}
