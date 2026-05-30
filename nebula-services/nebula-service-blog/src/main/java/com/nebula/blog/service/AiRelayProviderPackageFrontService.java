package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayPackageFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayPackageFrontVO;
import com.nebula.common.core.domain.PageResult;

import java.util.List;

/**
 * AI 中转服务商套餐前台服务
 */
public interface AiRelayProviderPackageFrontService {

    /**
     * 分页查询套餐
     */
    PageResult<AiRelayPackageFrontVO> pagePackages(AiRelayPackageFrontPageQuery query);

    /**
     * 套餐详情（含限制 / 模型）
     */
    AiRelayPackageFrontVO getPackage(Long id);

    /**
     * 列出指定服务商的所有上线套餐
     */
    List<AiRelayPackageFrontVO> listPackagesByProvider(Long providerId);
}
