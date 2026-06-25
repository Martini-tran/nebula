package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.SeriesGenerateRequest;
import com.nebula.blog.service.BlogSeriesGenerationAdminService;
import com.nebula.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台系列主题生成控制器
 * 调用AI博客Agent围绕主题规划系列选题大纲，并落库为系列与目录节点，返回新建系列ID。
 * 需配置并启用AI运行时（nebula.ai.enabled=true）后方可使用，否则生成服务将返回503。
 */
@RestController
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class SeriesGenerationAdminController extends AbstractAdminController {

    private final BlogSeriesGenerationAdminService seriesGenerationAdminService;

    /**
     * 生成系列主题并落库
     *
     * @param req 系列主题生成请求
     * @return 新建系列ID
     */
    @PostMapping("/generate")
    @SaCheckPermission("blog:series:generate")
    public R<Long> generate(@RequestBody @Valid SeriesGenerateRequest req) {
        return R.success(seriesGenerationAdminService.generate(req));
    }
}
