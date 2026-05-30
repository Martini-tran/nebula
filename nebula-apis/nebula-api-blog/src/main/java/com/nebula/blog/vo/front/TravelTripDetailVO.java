package com.nebula.blog.vo.front;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 游记详情VO（前台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TravelTripDetailVO extends TravelTripListVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 行程日列表（含打卡点）
     */
    private List<TravelTripDayVO> days;

    /**
     * 足迹（去重后的目的地）
     */
    private List<TravelDestinationSummaryVO> destinations;

    /**
     * 关联文章列表（已过滤为已发布+公开），按 postType (主→相关) + createTime 排序
     */
    private List<TravelTripPostSummaryVO> posts;
}
