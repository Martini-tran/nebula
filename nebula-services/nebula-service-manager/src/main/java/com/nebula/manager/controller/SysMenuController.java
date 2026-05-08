package com.nebula.manager.controller;

import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.MenuCreateRequest;
import com.nebula.manager.dto.MenuUpdateRequest;
import com.nebula.manager.service.SysMenuService;
import com.nebula.manager.vo.MenuRouteVO;
import com.nebula.manager.vo.MenuTreeVO;
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

@RestController
@RequestMapping("/menu")
public class SysMenuController {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/all")
    public R<List<MenuRouteVO>> all() {
        return R.success(menuService.listRoutes());
    }

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

    @GetMapping("/check-name")
    public R<Boolean> checkName(@RequestParam("name") String name,
                                @RequestParam(value = "id", required = false) Long id) {
        return R.success(menuService.existsByName(name, id));
    }

    @GetMapping("/check-path")
    public R<Boolean> checkPath(@RequestParam("path") String path,
                                @RequestParam(value = "id", required = false) Long id) {
        return R.success(menuService.existsByPath(path, id));
    }
}
