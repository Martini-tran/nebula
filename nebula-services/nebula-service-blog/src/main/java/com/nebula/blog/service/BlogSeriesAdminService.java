package com.nebula.blog.service;

import com.nebula.blog.dto.admin.SeriesAdminPageQuery;
import com.nebula.blog.dto.admin.SeriesCreateRequest;
import com.nebula.blog.dto.admin.SeriesUpdateRequest;
import com.nebula.blog.vo.admin.SeriesAdminVO;
import com.nebula.common.core.domain.PageResult;

/**
 * 博客系列管理服务接口（管理员端）
 */
public interface BlogSeriesAdminService {

    /**
     * 分页查询系列
     */
    PageResult<SeriesAdminVO> page(SeriesAdminPageQuery query);

    /**
     * 系列详情
     */
    SeriesAdminVO detail(Long id);

    /**
     * 创建系列
     *
     * @return 新建系列ID
     */
    Long create(SeriesCreateRequest req);

    /**
     * 更新系列
     */
    void update(Long id, SeriesUpdateRequest req);

    /**
     * 删除系列（级联删除目录与文章关联）
     */
    void delete(Long id);
}
