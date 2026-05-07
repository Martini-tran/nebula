package com.nebula.system.controller;

import com.nebula.common.core.domain.R;
import com.nebula.system.dto.MenuCreateRequest;
import com.nebula.system.dto.MenuUpdateRequest;
import com.nebula.system.service.SysMenuService;
import com.nebula.system.vo.MenuRouteVO;
import com.nebula.system.vo.MenuTreeVO;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单接口（context-path=/system，最终路径 /system/menu/...）
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
     * 当前用户能看到的菜单（这一轮不限制角色，返回全部启用且可见的目录 / 菜单）
     */
    @GetMapping("/all")
    public R<List<MenuRouteVO>> all() {
        return R.success(menuService.listRoutes());
    }

    /**
     * 管理后台用：返回完整的菜单树（含按钮、含禁用）
     * 对应前端 web-ele/src/api/system/menu#getMenuList
     */
    @GetMapping
    public R<List<MenuTreeVO>> tree() {
        return R.success(menuService.tree());
    }

    @PostMapping
    public R<Long> create(@RequestBody MenuCreateRequest request) {
        return R.success("create success", menuService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody MenuUpdateRequest request) {
        menuService.update(id, request);
        return R.success("update success", null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        menuService.delete(id);
        return R.success("delete success", null);
    }

    /**
     * 菜单 name 是否已存在；id 不为空时排除该菜单
     * 对应前端 isMenuNameExists(name, id?)
     */
    @GetMapping("/check-name")
    public R<Boolean> checkName(@RequestParam("name") String name,
                                @RequestParam(value = "id", required = false) Long id) {
        return R.success(menuService.existsByName(name, id));
    }

    /**
     * 菜单 path 是否已存在；id 不为空时排除该菜单
     * 对应前端 isMenuPathExists(path, id?)
     */
    @GetMapping("/check-path")
    public R<Boolean> checkPath(@RequestParam("path") String path,
                                @RequestParam(value = "id", required = false) Long id) {
        return R.success(menuService.existsByPath(path, id));
    }
}
