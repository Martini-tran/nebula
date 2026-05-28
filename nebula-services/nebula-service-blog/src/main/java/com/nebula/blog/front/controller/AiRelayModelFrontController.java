package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.AiRelayModelFrontPageQuery;
import com.nebula.blog.service.AiRelayModelFrontService;
import com.nebula.blog.vo.front.AiRelayModelFrontVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型控制器（前端）
 */
@RestController
@RequestMapping("/front/ai-relay/models")
@RequiredArgsConstructor
public class AiRelayModelFrontController extends AbstractFrontController {

    private final AiRelayModelFrontService modelFrontService;

    /**
     * 分页查询模型
     */
    @GetMapping
    public R<PageResult<AiRelayModelFrontVO>> page(AiRelayModelFrontPageQuery query) {
        return R.success(modelFrontService.pageModels(query));
    }

    /**
     * 模型详情
     */
    @GetMapping("/{id}")
    public R<AiRelayModelFrontVO> detail(@PathVariable Long id) {
        AiRelayModelFrontVO vo = modelFrontService.getModel(id);
        if (vo == null) {
            return R.fail(404, "模型不存在");
        }
        return R.success(vo);
    }
}
