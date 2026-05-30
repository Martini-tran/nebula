package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayProviderRecommendPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderRecommendRequest;
import com.nebula.blog.vo.admin.AiRelayProviderRecommendAdminVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI中转服务商推荐管理服务接口（管理员端）
 */
public interface AiRelayProviderRecommendAdminService {

    /**
     * 分页查询推荐
     */
    PageResult<AiRelayProviderRecommendAdminVO> page(AiRelayProviderRecommendPageQuery query);

    /**
     * 推荐详情
     */
    AiRelayProviderRecommendAdminVO detail(Long id);

    /**
     * 根据服务商ID查询推荐（一对一）
     */
    AiRelayProviderRecommendAdminVO detailByProviderId(Long providerId);

    /**
     * 创建推荐
     */
    Long create(AiRelayProviderRecommendRequest req);

    /**
     * 更新推荐
     */
    void update(Long id, AiRelayProviderRecommendRequest req);

    /**
     * 删除推荐
     */
    void delete(Long id);

    /**
     * 切换上下线状态
     */
    void updateStatus(Long id, Integer status);
}
