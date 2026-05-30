package com.nebula.blog.service;

import com.nebula.blog.dto.admin.TravelCheckinCreateRequest;
import com.nebula.blog.dto.admin.TravelCheckinUpdateRequest;
import com.nebula.blog.vo.admin.TravelCheckinAdminVO;

import java.util.List;

/**
 * 打卡点管理服务接口（管理员端）
 */
public interface TravelCheckinAdminService {

    List<TravelCheckinAdminVO> listByTripDay(Long tripDayId);

    TravelCheckinAdminVO detail(Long id);

    Long create(TravelCheckinCreateRequest req);

    void update(Long id, TravelCheckinUpdateRequest req);

    void delete(Long id);
}
