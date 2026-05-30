package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.AiRelayCompareFrontPageQuery;
import com.nebula.blog.service.AiRelayCompareFrontService;
import com.nebula.blog.vo.front.AiRelayCompareRowFrontVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 中转比价控制器（前端）
 *
 * <p>以 ai_relay_provider_package_limit 为主表的横向对比数据源。</p>
 */
@RestController
@RequestMapping("/front/ai-relay/compare")
@RequiredArgsConstructor
public class AiRelayCompareFrontController extends AbstractFrontController {

    private final AiRelayCompareFrontService compareFrontService;

    /**
     * 比价分页：每行=一条限额，附带所属套餐 / 服务商 /（若 modelId 指定）选定模型在该套餐下的输入输出单价
     */
    @GetMapping
    public R<PageResult<AiRelayCompareRowFrontVO>> page(AiRelayCompareFrontPageQuery query) {
        return R.success(compareFrontService.pageCompare(query));
    }
}
