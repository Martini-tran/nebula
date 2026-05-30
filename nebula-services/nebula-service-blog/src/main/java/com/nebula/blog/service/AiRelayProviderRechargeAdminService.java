package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayProviderRechargePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderRechargeRequest;
import com.nebula.blog.vo.admin.AiRelayProviderRechargeAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderRechargeStatsVO;
import com.nebula.common.core.domain.PageResult;

import java.util.List;

/**
 * AI中转服务商充值记录管理服务接口（管理员端）
 */
public interface AiRelayProviderRechargeAdminService {

    /**
     * 分页查询充值记录
     */
    PageResult<AiRelayProviderRechargeAdminVO> page(AiRelayProviderRechargePageQuery query);

    /**
     * 充值记录详情
     */
    AiRelayProviderRechargeAdminVO detail(Long id);

    /**
     * 创建充值记录
     */
    Long create(AiRelayProviderRechargeRequest req);

    /**
     * 更新充值记录
     */
    void update(Long id, AiRelayProviderRechargeRequest req);

    /**
     * 删除充值记录
     */
    void delete(Long id);

    /**
     * 按服务商汇总充值统计
     */
    List<AiRelayProviderRechargeStatsVO> statsByProvider(Long providerId);
}
