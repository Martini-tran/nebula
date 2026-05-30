package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.AiRelayPackageFrontPageQuery;
import com.nebula.blog.service.AiRelayProviderPackageFrontService;
import com.nebula.blog.vo.front.AiRelayPackageFrontVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 中转服务商套餐控制器（前端）
 */
@RestController
@RequestMapping("/front/ai-relay/packages")
@RequiredArgsConstructor
public class AiRelayProviderPackageFrontController extends AbstractFrontController {

    private final AiRelayProviderPackageFrontService packageFrontService;

    /**
     * 分页查询套餐
     */
    @GetMapping
    public R<PageResult<AiRelayPackageFrontVO>> page(AiRelayPackageFrontPageQuery query) {
        return R.success(packageFrontService.pagePackages(query));
    }

    /**
     * 列出指定服务商的全部上线套餐
     */
    @GetMapping("/by-provider")
    public R<List<AiRelayPackageFrontVO>> listByProvider(@RequestParam Long providerId) {
        return R.success(packageFrontService.listPackagesByProvider(providerId));
    }

    /**
     * 套餐详情（含限制 / 模型）
     */
    @GetMapping("/{id}")
    public R<AiRelayPackageFrontVO> detail(@PathVariable Long id) {
        AiRelayPackageFrontVO vo = packageFrontService.getPackage(id);
        if (vo == null) {
            return R.fail(404, "套餐不存在");
        }
        return R.success(vo);
    }
}
