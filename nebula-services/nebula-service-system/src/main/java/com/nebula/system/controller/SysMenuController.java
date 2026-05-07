package com.nebula.system.controller;

import com.nebula.common.core.domain.R;
import com.nebula.system.service.SysMenuService;
import com.nebula.system.vo.MenuRouteVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单接口（context-path=/system，最终路径 /system/menu/all）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/menu")
public class SysMenuController {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 当前用户能看到的菜单（这一轮不限制角色，返回全部启用且可见的菜单）
     */
    @GetMapping("/all")
    public R<List<MenuRouteVO>> all() {
        return R.success(menuService.listRoutes());
    }
}
