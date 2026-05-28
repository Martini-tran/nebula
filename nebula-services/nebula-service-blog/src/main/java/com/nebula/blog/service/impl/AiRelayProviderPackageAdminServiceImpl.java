package com.nebula.blog.service.impl;

import com.nebula.blog.dto.admin.AiRelayPackageModelRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageLimitRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackagePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPackageUpdateRequest;
import com.nebula.blog.mapper.AiRelayPackageModelMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageLimitMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageMapper;
import com.nebula.blog.service.AiRelayProviderPackageAdminService;
import com.nebula.blog.vo.admin.AiRelayPackageModelAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageLimitAdminVO;
import com.nebula.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * AI中转服务商套餐管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class AiRelayProviderPackageAdminServiceImpl implements AiRelayProviderPackageAdminService {

    private final AiRelayProviderPackageMapper packageMapper;
    private final AiRelayProviderPackageLimitMapper limitMapper;
    private final AiRelayPackageModelMapper packageModelMapper;

    @Override
    public PageResult<AiRelayProviderPackageAdminVO> page(AiRelayProviderPackagePageQuery query) {
        // TODO: 分页查询套餐
        return PageResult.empty(query.safePageNum(), query.safePageSize());
    }

    @Override
    public AiRelayProviderPackageAdminVO detail(Long id) {
        // TODO: 套餐详情
        return null;
    }

    @Override
    public Long create(AiRelayProviderPackageCreateRequest req) {
        // TODO: 创建套餐
        return null;
    }

    @Override
    public void update(Long id, AiRelayProviderPackageUpdateRequest req) {
        // TODO: 更新套餐
    }

    @Override
    public void delete(Long id) {
        // TODO: 删除套餐，级联清理 limits 和 package_models
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // TODO: 切换套餐状态
    }

    @Override
    public List<AiRelayProviderPackageLimitAdminVO> listLimits(Long packageId) {
        // TODO: 套餐限制列表
        return Collections.emptyList();
    }

    @Override
    public Long createLimit(Long packageId, AiRelayProviderPackageLimitRequest req) {
        // TODO: 新增套餐限制
        return null;
    }

    @Override
    public void updateLimit(Long packageId, Long limitId, AiRelayProviderPackageLimitRequest req) {
        // TODO: 更新套餐限制
    }

    @Override
    public void deleteLimit(Long packageId, Long limitId) {
        // TODO: 删除套餐限制
    }

    @Override
    public List<AiRelayPackageModelAdminVO> listModels(Long packageId) {
        // TODO: 套餐模型列表
        return Collections.emptyList();
    }

    @Override
    public Long createModel(Long packageId, AiRelayPackageModelRequest req) {
        // TODO: 新增套餐模型
        return null;
    }

    @Override
    public void updateModel(Long packageId, Long packageModelId, AiRelayPackageModelRequest req) {
        // TODO: 更新套餐模型
    }

    @Override
    public void deleteModel(Long packageId, Long packageModelId) {
        // TODO: 删除套餐模型
    }
}
