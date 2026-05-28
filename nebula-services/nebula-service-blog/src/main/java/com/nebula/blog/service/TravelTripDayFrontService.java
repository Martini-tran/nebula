package com.nebula.blog.service;

import com.nebula.blog.vo.front.TravelTripDayVO;

import java.util.List;

/**
 * 行程日前台服务
 */
public interface TravelTripDayFrontService {

    /**
     * 列出某游记下的行程日（带打卡点，仅查询已发布且公开的游记）
     */
    List<TravelTripDayVO> listByTrip(Long tripId);

    /**
     * 行程日详情（带打卡点）
     */
    TravelTripDayVO getDay(Long id);
}
