package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayModelFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayModelFrontVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI 模型前台服务
 */
public interface AiRelayModelFrontService {

    PageResult<AiRelayModelFrontVO> pageModels(AiRelayModelFrontPageQuery query);

    AiRelayModelFrontVO getModel(Long id);
}
