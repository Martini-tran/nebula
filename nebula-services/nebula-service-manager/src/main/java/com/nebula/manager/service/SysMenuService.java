package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.MenuCreateRequest;
import com.nebula.manager.dto.MenuUpdateRequest;
import com.nebula.manager.vo.MenuRouteVO;
import com.nebula.manager.vo.MenuTreeVO;
import java.util.List;

public interface SysMenuService {

    List<MenuRouteVO> listRoutes();

    List<MenuTreeVO> tree();

    Long create(MenuCreateRequest request);

    void update(Long id, MenuUpdateRequest request);

    void delete(Long id);

    boolean existsByName(String name, Long excludeId);

    boolean existsByPath(String path, Long excludeId);
}
