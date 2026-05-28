package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.AiRelayPackageTypeFrontService;
import com.nebula.blog.vo.front.AiRelayPackageTypeFrontVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 中转套餐类型字典控制器（前端）
 */
@RestController
@RequestMapping("/front/ai-relay/package-types")
@RequiredArgsConstructor
public class AiRelayPackageTypeFrontController extends AbstractFrontController {

    private final AiRelayPackageTypeFrontService packageTypeFrontService;

    /**
     * 列出全部上线的套餐类型，前端用于筛选
     */
    @GetMapping
    public R<List<AiRelayPackageTypeFrontVO>> list() {
        return R.success(packageTypeFrontService.listPackageTypes());
    }
}
