package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.service.SpaceSearchService;
import com.nebula.space.vo.me.SearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台全局搜索控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/search")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceSearchController {

    private final SpaceSearchService searchService;

    /**
     * 各模块召回的候选，前端再按同一套规则过滤、打分、排序
     */
    @GetMapping
    public R<SearchResultVO> search(@RequestParam(defaultValue = "") String q) {
        return R.success(searchService.search(q));
    }
}
