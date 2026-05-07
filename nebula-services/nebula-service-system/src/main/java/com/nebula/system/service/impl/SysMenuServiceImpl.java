package com.nebula.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.system.entity.SysMenu;
import com.nebula.system.mapper.SysMenuMapper;
import com.nebula.system.service.SysMenuService;
import com.nebula.system.vo.MenuMetaVO;
import com.nebula.system.vo.MenuRouteVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SysMenuServiceImpl implements SysMenuService {

    /**
     * 菜单类型：1 目录 / 2 菜单 / 3 按钮
     * 按钮不参与路由生成，过滤掉
     */
    private static final int MENU_TYPE_DIR = 1;
    private static final int MENU_TYPE_MENU = 2;

    private static final String LAYOUT_BASIC = "BasicLayout";

    private final SysMenuMapper menuMapper;

    public SysMenuServiceImpl(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    @Override
    public List<MenuRouteVO> listRoutes() {
        // status=1 且 visible=1 且类型为目录或菜单
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getStatus, 1)
                .eq(SysMenu::getVisible, 1)
                .in(SysMenu::getMenuType, MENU_TYPE_DIR, MENU_TYPE_MENU)
                .orderByAsc(SysMenu::getSort);
        List<SysMenu> menus = menuMapper.selectList(wrapper);

        // 按 parentId 分组
        Map<Long, List<SysMenu>> grouped = new HashMap<>();
        for (SysMenu m : menus) {
            grouped.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(),
                    k -> new ArrayList<>()).add(m);
        }

        // 自顶向下递归构建树（root parentId = 0）
        return buildTree(0L, grouped);
    }

    private List<MenuRouteVO> buildTree(Long parentId, Map<Long, List<SysMenu>> grouped) {
        List<SysMenu> children = grouped.get(parentId);
        if (children == null || children.isEmpty()) {
            return null;
        }
        children.sort(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()));

        List<MenuRouteVO> result = new ArrayList<>(children.size());
        for (SysMenu m : children) {
            MenuRouteVO route = new MenuRouteVO();
            route.setPath(m.getPath());
            route.setName(toRouteName(m.getPath()));
            // 目录走 layout 名，access.ts 在挂到 root 时会 delete component；
            // 菜单 component 是视图相对路径，前端 normalizeViewPath + pageMap 会解析
            if (m.getMenuType() != null && m.getMenuType() == MENU_TYPE_DIR) {
                route.setComponent(blankToDefault(m.getComponent(), LAYOUT_BASIC));
            } else {
                route.setComponent(m.getComponent());
            }

            MenuMetaVO meta = new MenuMetaVO();
            meta.setTitle(m.getMenuName());
            meta.setIcon(blankToNull(m.getIcon()));
            meta.setOrder(m.getSort());
            route.setMeta(meta);

            route.setChildren(buildTree(m.getId(), grouped));
            result.add(route);
        }
        return result;
    }

    /**
     * 由路径派生 Vue Router name：/system/user → SystemUser，/dashboard → Dashboard
     */
    private static String toRouteName(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "Root";
        }
        String cleaned = path.replaceAll("[^A-Za-z0-9]+", "-");
        StringBuilder sb = new StringBuilder();
        for (String part : cleaned.split("-")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.length() == 0 ? "Root" : sb.toString();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
