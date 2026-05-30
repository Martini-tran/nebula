package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayModelPageQuery;
import com.nebula.blog.dto.admin.AiRelayModelRequest;
import com.nebula.blog.vo.admin.AiRelayModelAdminVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI模型管理服务接口（管理员端）
 */
public interface AiRelayModelAdminService {

    /**
     * 分页查询模型
     */
    PageResult<AiRelayModelAdminVO> page(AiRelayModelPageQuery query);

    /**
     * 模型详情
     */
    AiRelayModelAdminVO detail(Long id);

    /**
     * 创建模型
     */
    Long create(AiRelayModelRequest req);

    /**
     * 更新模型
     */
    void update(Long id, AiRelayModelRequest req);

    /**
     * 删除模型
     */
    void delete(Long id);

    /**
     * 切换模型状态
     */
    void updateStatus(Long id, Integer status);
}
