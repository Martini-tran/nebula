package com.nebula.blog.service;

import com.nebula.blog.dto.front.TravelTripPageQuery;
import com.nebula.blog.vo.front.TravelTripDetailVO;
import com.nebula.blog.vo.front.TravelTripListResponse;
import com.nebula.blog.vo.front.TravelTripListVO;

import java.util.List;

/**
 * 游记前台服务接口
 */
public interface TravelTripFrontService {

    /**
     * 分页查询已发布的公开游记
     */
    TravelTripListResponse listTrips(TravelTripPageQuery query);

    /**
     * 关键词搜索游记
     */
    TravelTripListResponse searchTrips(TravelTripPageQuery query);

    /**
     * 热门游记（按浏览量）
     */
    List<TravelTripListVO> getHotTrips(int limit);

    /**
     * 游记详情，未找到/非公开返回 null
     */
    TravelTripDetailVO getTripDetail(String slug);
}
