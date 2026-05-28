package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayPackageModelRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageLimitRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackagePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPackageUpdateRequest;
import com.nebula.blog.vo.admin.AiRelayPackageModelAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageLimitAdminVO;
import com.nebula.common.core.domain.PageResult;

import java.util.List;

/**
 * AI中转服务商套餐管理服务接口（管理员端）
 */
public interface AiRelayProviderPackageAdminService {

    /**
     * 分页查询套餐
     */
    PageResult<AiRelayProviderPackageAdminVO> page(AiRelayProviderPackagePageQuery query);

    /**
     * 套餐详情
     */
    AiRelayProviderPackageAdminVO detail(Long id);

    /**
     * 创建套餐
     */
    Long create(AiRelayProviderPackageCreateRequest req);

    /**
     * 更新套餐
     */
    void update(Long id, AiRelayProviderPackageUpdateRequest req);

    /**
     * 删除套餐
     */
    void delete(Long id);

    /**
     * 切换套餐状态
     */
    void updateStatus(Long id, Integer status);

    // ============ 套餐限制（子资源） ============

    /**
     * 获取套餐限制列表
     */
    List<AiRelayProviderPackageLimitAdminVO> listLimits(Long packageId);

    /**
     * 创建套餐限制
     */
    Long createLimit(Long packageId, AiRelayProviderPackageLimitRequest req);

    /**
     * 更新套餐限制
     */
    void updateLimit(Long packageId, Long limitId, AiRelayProviderPackageLimitRequest req);

    /**
     * 删除套餐限制
     */
    void deleteLimit(Long packageId, Long limitId);

    // ============ 套餐模型（子资源） ============

    /**
     * 获取套餐支持的模型
     */
    List<AiRelayPackageModelAdminVO> listModels(Long packageId);

    /**
     * 新增套餐支持的模型
     */
    Long createModel(Long packageId, AiRelayPackageModelRequest req);

    /**
     * 更新套餐模型配置
     */
    void updateModel(Long packageId, Long packageModelId, AiRelayPackageModelRequest req);

    /**
     * 移除套餐模型
     */
    void deleteModel(Long packageId, Long packageModelId);
}
