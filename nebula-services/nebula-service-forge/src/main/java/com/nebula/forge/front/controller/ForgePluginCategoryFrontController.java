package com.nebula.forge.front.controller;

import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractFrontController;
import com.nebula.forge.service.ForgePluginCategoryFrontService;
import com.nebula.forge.vo.front.ForgePluginCategoryFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件分类前台控制器（公开访问）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/front/plugin-categories")
@RequiredArgsConstructor
public class ForgePluginCategoryFrontController extends AbstractFrontController {

    private final ForgePluginCategoryFrontService categoryFrontService;

    /**
     * 查询启用中的分类列表
     */
    @GetMapping({"", "/list"})
    public R<List<ForgePluginCategoryFrontVO>> list() {
        return R.success(categoryFrontService.list());
    }
}
