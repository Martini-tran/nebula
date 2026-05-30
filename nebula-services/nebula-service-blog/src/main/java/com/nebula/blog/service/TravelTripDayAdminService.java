package com.nebula.blog.service;

import com.nebula.blog.dto.admin.TravelTripDayCreateRequest;
import com.nebula.blog.dto.admin.TravelTripDayUpdateRequest;
import com.nebula.blog.vo.admin.TravelTripDayAdminVO;

import java.util.List;

/**
 * 行程日管理服务接口（管理员端）
 */
public interface TravelTripDayAdminService {

    /**
     * 列出某游记下所有行程日（按 dayNumber / sortOrder 升序）
     */
    List<TravelTripDayAdminVO> listByTrip(Long tripId);

    TravelTripDayAdminVO detail(Long id);

    Long create(TravelTripDayCreateRequest req);

    void update(Long id, TravelTripDayUpdateRequest req);

    /**
     * 删除行程日（级联清理 checkin）
     */
    void delete(Long id);
}
