package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayProviderFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayProviderFrontVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI 中转服务商前台服务
 */
public interface AiRelayProviderFrontService {

    /**
     * 分页查询服务商，返回带套餐 / 优势 / 支付方式 / 模型聚合的卡片数据
     */
    PageResult<AiRelayProviderFrontVO> pageProviders(AiRelayProviderFrontPageQuery query);

    /**
     * 服务商详情（与列表同结构，保留独立入口便于后续扩展）
     */
    AiRelayProviderFrontVO getProvider(Long id);
}
