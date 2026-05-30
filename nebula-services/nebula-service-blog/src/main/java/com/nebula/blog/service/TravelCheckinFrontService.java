package com.nebula.blog.service;

import com.nebula.blog.vo.front.TravelCheckinVO;

import java.util.List;

/**
 * 打卡点前台服务
 */
public interface TravelCheckinFrontService {

    /**
     * 列出某行程日下的全部打卡点（仅当父游记已发布且公开）
     */
    List<TravelCheckinVO> listByTripDay(Long tripDayId);

    /**
     * 打卡点详情
     */
    TravelCheckinVO getCheckin(Long id);
}
