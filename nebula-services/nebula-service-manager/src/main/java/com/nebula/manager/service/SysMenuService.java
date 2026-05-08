package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.MenuCreateRequest;
import com.nebula.manager.dto.MenuUpdateRequest;
import com.nebula.manager.vo.MenuRouteVO;
import com.nebula.manager.vo.MenuTreeVO;
import java.util.List;

/**
 * 系统菜单服务接口
 * 定义菜单的增删改查、树形结构构建、路由列表生成等业务方法
 *
 * @author nebula
 */
public interface SysMenuService {

    /**
     * 获取所有菜单路由信息
     * 返回扁平化的菜单路由列表，用于前端路由配置
     *
     * @return 菜单路由列表
     */
    List<MenuRouteVO> listRoutes();

    /**
     * 获取菜单树形结构
     * 返回层级嵌套的菜单树结构，用于前端菜单导航展示
     *
     * @return 菜单树结构列表
     */
    List<MenuTreeVO> tree();

    /**
     * 创建新菜单
     * 根据菜单创建请求数据保存新的菜单记录
     *
     * @param request 菜单创建请求对象，包含菜单基本信息
     * @return 创建成功的菜单ID
     */
    Long create(MenuCreateRequest request);

    /**
     * 更新菜单信息
     * 根据菜单ID修改指定菜单的数据
     *
     * @param id      菜单ID
     * @param request 菜单更新请求对象，包含要修改的字段
     */
    void update(Long id, MenuUpdateRequest request);

    /**
     * 删除菜单
     * 根据菜单ID删除对应的菜单记录
     *
     * @param id 菜单ID
     */
    void delete(Long id);

    /**
     * 检查菜单名称是否存在
     * 用于表单验证，判断指定名称的菜单是否已存在（排除指定ID的菜单）
     *
     * @param name      要检查的菜单名称
     * @param excludeId 要排除的菜单ID，用于编辑时排除当前菜单自身
     * @return 菜单名称是否存在
     */
    boolean existsByName(String name, Long excludeId);

    /**
     * 检查菜单路径是否存在
     * 用于表单验证，判断指定路径的菜单是否已存在（排除指定ID的菜单）
     *
     * @param path      要检查的菜单路径
     * @param excludeId 要排除的菜单ID，用于编辑时排除当前菜单自身
     * @return 菜单路径是否存在
     */
    boolean existsByPath(String path, Long excludeId);
}
