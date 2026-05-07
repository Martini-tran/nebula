package com.nebula.system.service;

import com.nebula.system.dto.MenuCreateRequest;
import com.nebula.system.dto.MenuUpdateRequest;
import com.nebula.system.vo.MenuRouteVO;
import com.nebula.system.vo.MenuTreeVO;
import java.util.List;

/**
 * 系统菜单业务接口
 *
 * @author nebula
 */
public interface SysMenuService {

    /**
     * 拉取启用且可见的菜单（目录 + 菜单），转成前端可消费的路由树
     */
    List<MenuRouteVO> listRoutes();

    /**
     * 管理后台用：返回包含按钮在内的全量菜单树
     */
    List<MenuTreeVO> tree();

    /**
     * 创建菜单，返回主键
     */
    Long create(MenuCreateRequest request);

    /**
     * 更新菜单
     */
    void update(Long id, MenuUpdateRequest request);

    /**
     * 删除菜单（存在子菜单时不允许删除）
     */
    void delete(Long id);

    /**
     * 路由 name 是否已存在；excludeId 不为空时排除该 id
     */
    boolean existsByName(String name, Long excludeId);

    /**
     * 路由 path 是否已存在；excludeId 不为空时排除该 id
     */
    boolean existsByPath(String path, Long excludeId);
}
