package com.nebula.blog.service.impl;

import com.nebula.blog.dto.admin.AiRelayPackageTypeRequest;
import com.nebula.blog.mapper.AiRelayPackageTypeMapper;
import com.nebula.blog.service.AiRelayPackageTypeAdminService;
import com.nebula.blog.vo.admin.AiRelayPackageTypeAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * AI中转套餐类型 管理服务实现（字典）
 */
@Service
@RequiredArgsConstructor
public class AiRelayPackageTypeAdminServiceImpl implements AiRelayPackageTypeAdminService {

    private final AiRelayPackageTypeMapper packageTypeMapper;

    @Override
    public List<AiRelayPackageTypeAdminVO> list(Integer status) {
        // TODO: 套餐类型列表
        return Collections.emptyList();
    }

    @Override
    public AiRelayPackageTypeAdminVO detail(Long id) {
        // TODO: 套餐类型详情
        return null;
    }

    @Override
    public Long create(AiRelayPackageTypeRequest req) {
        // TODO: 创建套餐类型
        return null;
    }

    @Override
    public void update(Long id, AiRelayPackageTypeRequest req) {
        // TODO: 更新套餐类型
    }

    @Override
    public void delete(Long id) {
        // TODO: 删除套餐类型，需校验是否有套餐引用
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // TODO: 切换套餐类型状态
    }
}
