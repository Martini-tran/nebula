package com.nebula.blog.service.impl;

import com.nebula.blog.dto.admin.AiRelayModelPageQuery;
import com.nebula.blog.dto.admin.AiRelayModelRequest;
import com.nebula.blog.mapper.AiRelayModelMapper;
import com.nebula.blog.service.AiRelayModelAdminService;
import com.nebula.blog.vo.admin.AiRelayModelAdminVO;
import com.nebula.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI模型管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class AiRelayModelAdminServiceImpl implements AiRelayModelAdminService {

    private final AiRelayModelMapper modelMapper;

    @Override
    public PageResult<AiRelayModelAdminVO> page(AiRelayModelPageQuery query) {
        // TODO: 分页查询模型
        return PageResult.empty(query.safePageNum(), query.safePageSize());
    }

    @Override
    public AiRelayModelAdminVO detail(Long id) {
        // TODO: 模型详情
        return null;
    }

    @Override
    public Long create(AiRelayModelRequest req) {
        // TODO: 创建模型，校验 code 唯一
        return null;
    }

    @Override
    public void update(Long id, AiRelayModelRequest req) {
        // TODO: 更新模型
    }

    @Override
    public void delete(Long id) {
        // TODO: 删除模型，需校验是否被套餐引用
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // TODO: 切换模型状态
    }
}
