package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayCompareFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayCompareRowFrontVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI 中转比价（前台）服务
 *
 * <p>以 ai_relay_provider_package_limit 为主表，按所选模型横向比较不同中转站的额度与单价。</p>
 */
public interface AiRelayCompareFrontService {

    PageResult<AiRelayCompareRowFrontVO> pageCompare(AiRelayCompareFrontPageQuery query);
}
