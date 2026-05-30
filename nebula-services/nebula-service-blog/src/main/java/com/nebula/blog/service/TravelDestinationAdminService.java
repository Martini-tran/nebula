package com.nebula.blog.service;

import com.nebula.blog.dto.admin.TravelDestinationCreateRequest;
import com.nebula.blog.dto.admin.TravelDestinationUpdateRequest;
import com.nebula.blog.vo.admin.TravelDestinationAdminVO;

import java.util.List;

/**
 * 旅游目的地管理服务接口（管理员端）
 */
public interface TravelDestinationAdminService {

    /**
     * 获取目的地树（按 sort_order 升序）
     */
    List<TravelDestinationAdminVO> tree();

    /**
     * 目的地详情
     */
    TravelDestinationAdminVO detail(Long id);

    /**
     * 创建目的地
     *
     * @return 新建ID
     */
    Long create(TravelDestinationCreateRequest req);

    /**
     * 更新目的地
     */
    void update(Long id, TravelDestinationUpdateRequest req);

    /**
     * 删除目的地（要求无子节点）
     */
    void delete(Long id);
}
