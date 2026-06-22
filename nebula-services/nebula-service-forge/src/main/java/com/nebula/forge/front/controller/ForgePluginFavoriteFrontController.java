package com.nebula.forge.front.controller;

import com.nebula.common.core.domain.PageQuery;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractFrontController;
import com.nebula.forge.service.ForgePluginFavoriteFrontService;
import com.nebula.forge.vo.front.ForgePluginFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件收藏前台控制器（需登录）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/front")
@RequiredArgsConstructor
public class ForgePluginFavoriteFrontController extends AbstractFrontController {

    private final ForgePluginFavoriteFrontService favoriteFrontService;

    /**
     * 收藏插件
     */
    @PostMapping("/plugins/{id}/favorite")
    public R<Void> add(@PathVariable Long id) {
        favoriteFrontService.add(id);
        return R.success();
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/plugins/{id}/favorite")
    public R<Void> remove(@PathVariable Long id) {
        favoriteFrontService.remove(id);
        return R.success();
    }

    /**
     * 我的收藏列表（分页）
     */
    @GetMapping("/me/favorites")
    public R<PageResult<ForgePluginFrontVO>> myFavorites(@ModelAttribute PageQuery query) {
        return R.success(favoriteFrontService.myFavorites(query));
    }
}
