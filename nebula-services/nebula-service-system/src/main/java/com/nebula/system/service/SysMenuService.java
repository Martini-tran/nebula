package com.nebula.system.service;

import com.nebula.system.vo.MenuRouteVO;
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
}
