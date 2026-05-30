package com.nebula.blog.service;

import com.nebula.blog.dto.admin.TravelTripAdminPageQuery;
import com.nebula.blog.dto.admin.TravelTripCreateRequest;
import com.nebula.blog.dto.admin.TravelTripPostBindRequest;
import com.nebula.blog.dto.admin.TravelTripStatusUpdateRequest;
import com.nebula.blog.dto.admin.TravelTripUpdateRequest;
import com.nebula.blog.vo.admin.TravelTripAdminVO;
import com.nebula.blog.vo.admin.TravelTripPostVO;
import com.nebula.common.core.domain.PageResult;

import java.util.List;

/**
 * 游记管理服务接口（管理员端）
 */
public interface TravelTripAdminService {

    PageResult<TravelTripAdminVO> page(TravelTripAdminPageQuery query);

    TravelTripAdminVO detail(Long id);

    Long create(TravelTripCreateRequest req);

    void update(Long id, TravelTripUpdateRequest req);

    void updateStatus(Long id, TravelTripStatusUpdateRequest req);

    /**
     * 删除游记（级联清理 trip_day / checkin / trip_blog_post）
     */
    void delete(Long id);

    /**
     * 查询游记关联的博客文章列表
     */
    List<TravelTripPostVO> listPosts(Long tripId);

    /**
     * 全量替换游记的博客文章关联
     */
    void bindPosts(Long tripId, TravelTripPostBindRequest req);
}
