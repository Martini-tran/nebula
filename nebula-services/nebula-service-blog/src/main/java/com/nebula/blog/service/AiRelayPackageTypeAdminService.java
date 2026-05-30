package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayPackageTypeRequest;
import com.nebula.blog.vo.admin.AiRelayPackageTypeAdminVO;

import java.util.List;

/**
 * AI中转套餐类型 管理服务接口（字典）
 */
public interface AiRelayPackageTypeAdminService {

    /**
     * 列表查询（不分页）
     */
    List<AiRelayPackageTypeAdminVO> list(Integer status);

    /**
     * 详情
     */
    AiRelayPackageTypeAdminVO detail(Long id);

    /**
     * 创建
     */
    Long create(AiRelayPackageTypeRequest req);

    /**
     * 更新
     */
    void update(Long id, AiRelayPackageTypeRequest req);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 切换状态
     */
    void updateStatus(Long id, Integer status);
}
