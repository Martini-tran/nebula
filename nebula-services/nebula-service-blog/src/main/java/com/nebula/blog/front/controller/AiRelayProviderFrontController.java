package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.AiRelayProviderFrontPageQuery;
import com.nebula.blog.service.AiRelayProviderFrontService;
import com.nebula.blog.vo.front.AiRelayProviderFrontVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 中转服务商控制器（前端）
 */
@RestController
@RequestMapping("/front/ai-relay/providers")
@RequiredArgsConstructor
public class AiRelayProviderFrontController extends AbstractFrontController {

    private final AiRelayProviderFrontService providerFrontService;

    /**
     * 分页查询服务商，返回带套餐 / 优势 / 支付方式 / 模型聚合的卡片数据
     */
    @GetMapping
    public R<PageResult<AiRelayProviderFrontVO>> page(AiRelayProviderFrontPageQuery query) {
        return R.success(providerFrontService.pageProviders(query));
    }

    /**
     * 服务商详情
     */
    @GetMapping("/{id}")
    public R<AiRelayProviderFrontVO> detail(@PathVariable Long id) {
        AiRelayProviderFrontVO vo = providerFrontService.getProvider(id);
        if (vo == null) {
            return R.fail(404, "服务商不存在");
        }
        return R.success(vo);
    }
}
