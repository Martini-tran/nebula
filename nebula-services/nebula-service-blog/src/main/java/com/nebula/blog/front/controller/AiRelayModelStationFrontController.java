package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.AiRelayModelStationFrontPageQuery;
import com.nebula.blog.service.AiRelayModelStationFrontService;
import com.nebula.blog.vo.front.AiRelayModelStationFrontVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 中转「模型选择站点」控制器（前端）
 *
 * <p>以 ai_relay_package_model 为主表：先选定模型，再横向对比支持该模型的各主站套餐、消耗倍率
 * 与实付每百万 token 单价。</p>
 */
@RestController
@RequestMapping("/front/ai-relay/model-stations")
@RequiredArgsConstructor
public class AiRelayModelStationFrontController extends AbstractFrontController {

    private final AiRelayModelStationFrontService modelStationFrontService;

    /**
     * 分页查询：每行=一条「套餐 × 模型」绑定，附带所属套餐 / 主站，以及实付输入输出单价（含倍率）
     */
    @GetMapping
    public R<PageResult<AiRelayModelStationFrontVO>> page(AiRelayModelStationFrontPageQuery query) {
        return R.success(modelStationFrontService.pageModelStations(query));
    }
}
