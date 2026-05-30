package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayRecommendFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayRecommendFrontVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI 中转推荐前台服务
 */
public interface AiRelayRecommendFrontService {

    /**
     * 分页查询推荐（仅返回上线状态的推荐 + 上线状态的服务商）
     */
    PageResult<AiRelayRecommendFrontVO> pageRecommends(AiRelayRecommendFrontPageQuery query);

    /**
     * 推荐详情（按推荐 ID）
     */
    AiRelayRecommendFrontVO getRecommend(Long id);

    /**
     * 按服务商 ID 获取推荐（一对一）
     */
    AiRelayRecommendFrontVO getByProvider(Long providerId);
}
