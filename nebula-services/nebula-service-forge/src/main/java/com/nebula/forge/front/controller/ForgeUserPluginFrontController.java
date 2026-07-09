package com.nebula.forge.front.controller;

import com.nebula.common.core.domain.PageQuery;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractFrontController;
import com.nebula.forge.dto.front.ForgePluginInstallRequest;
import com.nebula.forge.dto.front.ForgeUserPluginUpdateRequest;
import com.nebula.forge.service.ForgeUserPluginFrontService;
import com.nebula.forge.vo.front.ForgeUserPluginFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的插件 / 安装上报前台控制器（需登录）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/front")
@RequiredArgsConstructor
public class ForgeUserPluginFrontController extends AbstractFrontController {

    private final ForgeUserPluginFrontService userPluginFrontService;

    /**
     * 安装上报
     */
    @PostMapping("/plugins/{id}/install")
    public R<Void> install(@PathVariable Long id,
                           @RequestBody(required = false) ForgePluginInstallRequest req) {
        userPluginFrontService.install(id, req);
        return R.success();
    }

    /**
     * 卸载上报
     */
    @DeleteMapping("/plugins/{id}/install")
    public R<Void> uninstall(@PathVariable Long id) {
        userPluginFrontService.uninstall(id);
        return R.success();
    }

    /**
     * 我的插件列表（分页）
     */
    @GetMapping("/me/plugins")
    public R<PageResult<ForgeUserPluginFrontVO>> myPlugins(@ModelAttribute PageQuery query) {
        return R.success(userPluginFrontService.myPlugins(query));
    }

    /**
     * 更新我的插件（启用/自动更新/配置/窗口状态）
     */
    @PutMapping("/me/plugins/{id}")
    public R<Void> update(@PathVariable Long id,
                          @RequestBody ForgeUserPluginUpdateRequest req) {
        userPluginFrontService.update(id, req);
        return R.success();
    }

    /**
     * 上报最近使用时间
     */
    @PostMapping("/me/plugins/{id}/used")
    public R<Void> touchUsed(@PathVariable Long id) {
        userPluginFrontService.touchUsed(id);
        return R.success();
    }
}
