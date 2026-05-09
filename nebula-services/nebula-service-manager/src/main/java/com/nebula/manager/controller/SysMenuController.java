package com.nebula.manager.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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

/**
 * 系统菜单控制器
 * 提供菜单的增删改查、树形结构展示、路由列表等功能
 *
 * @author nebula
 */
@RestController
@RequestMapping("/menu")
public class SysMenuController {

    /**
     * 菜单服务业务层注入
     * 负责处理菜单相关的业务逻辑
     */
    private final SysMenuService menuService;

    /**
     * 构造函数注入菜单服务
     *
     * @param menuService 菜单服务实例
     */
    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 获取所有菜单路由信息
     * 返回扁平化的菜单路由列表，用于前端路由配置
     *
     * @return 菜单路由列表响应结果
     */
    @GetMapping("/all")
    public R<List<MenuRouteVO>> all() {
        return R.success(menuService.listRoutes());
    }

    /**
     * 获取菜单树形结构
     * 返回层级嵌套的菜单树结构，用于前端菜单导航展示
     *
     * @return 菜单树结构响应结果
     */
    @GetMapping
    public R<List<MenuTreeVO>> tree() {
        return R.success(menuService.tree());
    }

    /**
     * 创建新菜单
     * 接收菜单创建请求数据，保存新的菜单记录
     *
     * @param request 菜单创建请求对象，包含菜单基本信息
     * @return 创建成功的菜单ID
     */
    @SaCheckPermission("system:menu:add")
    @PostMapping
    public R<Long> create(@RequestBody MenuCreateRequest request) {
        return R.success("create success", menuService.create(request));
    }

    /**
     * 更新菜单信息
     * 根据菜单ID修改指定菜单的数据
     *
     * @param id      菜单ID路径参数
     * @param request 菜单更新请求对象，包含要修改的字段
     * @return 更新操作结果
     */
    @SaCheckPermission("system:menu:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody MenuUpdateRequest request) {
        // 调用菜单服务执行更新操作
        menuService.update(id, request);
        // 返回更新成功响应
        return R.success("update success", null);
    }

    /**
     * 删除菜单
     * 根据菜单ID删除对应的菜单记录
     *
     * @param id 菜单ID路径参数
     * @return 删除操作结果
     */
    @SaCheckPermission("system:menu:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        // 调用菜单服务执行删除操作
        menuService.delete(id);
        // 返回删除成功响应
        return R.success("delete success", null);
    }

    /**
     * 检查菜单名称是否存在
     * 用于表单验证，判断指定名称的菜单是否已存在
     *
     * @param name 要检查的菜单名称
     * @param id   菜单ID，用于编辑时排除当前菜单自身
     * @return 菜单名称是否存在
     */
    @GetMapping("/check-name")
    public R<Boolean> checkName(@RequestParam("name") String name,
                                @RequestParam(value = "id", required = false) Long id) {
        return R.success(menuService.existsByName(name, id));
    }

    /**
     * 检查菜单路径是否存在
     * 用于表单验证，判断指定路径的菜单是否已存在
     *
     * @param path 要检查的菜单路径
     * @param id   菜单ID，用于编辑时排除当前菜单自身
     * @return 菜单路径是否存在
     */
    @GetMapping("/check-path")
    public R<Boolean> checkPath(@RequestParam("path") String path,
                                @RequestParam(value = "id", required = false) Long id) {
        return R.success(menuService.existsByPath(path, id));
    }
}
