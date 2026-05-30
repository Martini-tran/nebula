package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayModelFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayModelFrontVO;
import com.nebula.blog.vo.front.AiRelayOptionVO;
import com.nebula.common.core.domain.PageResult;

import java.util.List;

/**
 * AI 模型前台服务
 */
public interface AiRelayModelFrontService {

    PageResult<AiRelayModelFrontVO> pageModels(AiRelayModelFrontPageQuery query);

    AiRelayModelFrontVO getModel(Long id);

    /**
     * 列出全部上线模型对应的厂商筛选项（去重，归一化为小写关键词）
     */
    List<AiRelayOptionVO> listVendorOptions();
}
