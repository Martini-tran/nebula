package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.AiRelayRecommendFrontPageQuery;
import com.nebula.blog.service.AiRelayRecommendFrontService;
import com.nebula.blog.vo.front.AiRelayRecommendFrontVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 中转推荐控制器（前台）
 */
@RestController
@RequestMapping("/front/ai-relay/recommends")
@RequiredArgsConstructor
public class AiRelayRecommendFrontController extends AbstractFrontController {

    private final AiRelayRecommendFrontService recommendFrontService;

    /**
     * 分页查询推荐
     */
    @GetMapping
    public R<PageResult<AiRelayRecommendFrontVO>> page(AiRelayRecommendFrontPageQuery query) {
        return R.success(recommendFrontService.pageRecommends(query));
    }

    /**
     * 推荐详情
     */
    @GetMapping("/{id}")
    public R<AiRelayRecommendFrontVO> detail(@PathVariable Long id) {
        AiRelayRecommendFrontVO vo = recommendFrontService.getRecommend(id);
        if (vo == null) {
            return R.fail(404, "推荐不存在或已下线");
        }
        return R.success(vo);
    }

    /**
     * 按服务商 ID 获取推荐
     */
    @GetMapping("/by-provider/{providerId}")
    public R<AiRelayRecommendFrontVO> byProvider(@PathVariable Long providerId) {
        AiRelayRecommendFrontVO vo = recommendFrontService.getByProvider(providerId);
        if (vo == null) {
            return R.fail(404, "该服务商暂无推荐");
        }
        return R.success(vo);
    }
}
