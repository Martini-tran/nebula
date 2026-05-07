package com.nebula.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.exception.BizException;
import com.nebula.system.dto.MenuCreateRequest;
import com.nebula.system.dto.MenuUpdateRequest;
import com.nebula.system.entity.SysMenu;
import com.nebula.system.enums.MenuTypeEnum;
import com.nebula.system.enums.SystemResultCode;
import com.nebula.system.mapper.SysMenuMapper;
import com.nebula.system.service.SysMenuService;
import com.nebula.system.vo.MenuMetaVO;
import com.nebula.system.vo.MenuRouteVO;
import com.nebula.system.vo.MenuTreeVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SysMenuServiceImpl implements SysMenuService {

    /**
     * 菜单类型：1 目录 / 2 菜单 / 3 按钮 / 4 内嵌 / 5 外链
     * /menu/all 路由生成只取目录 + 菜单
     */
    private static final int MENU_TYPE_DIR = 1;
    private static final int MENU_TYPE_MENU = 2;

    private static final String LAYOUT_BASIC = "BasicLayout";

    private final SysMenuMapper menuMapper;

    public SysMenuServiceImpl(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    // ===== 路由生成（保留原行为，给 /menu/all 使用） =====

    @Override
    public List<MenuRouteVO> listRoutes() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getStatus, 1)
                .eq(SysMenu::getVisible, 1)
                .in(SysMenu::getMenuType, MENU_TYPE_DIR, MENU_TYPE_MENU)
                .orderByAsc(SysMenu::getSort);
        List<SysMenu> menus = menuMapper.selectList(wrapper);

        Map<Long, List<SysMenu>> grouped = new HashMap<>();
        for (SysMenu m : menus) {
            grouped.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(),
                    k -> new ArrayList<>()).add(m);
        }
        return buildRouteTree(0L, grouped);
    }

    private List<MenuRouteVO> buildRouteTree(Long parentId, Map<Long, List<SysMenu>> grouped) {
        List<SysMenu> children = grouped.get(parentId);
        if (children == null || children.isEmpty()) {
            return null;
        }
        children.sort(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()));

        List<MenuRouteVO> result = new ArrayList<>(children.size());
        for (SysMenu m : children) {
            MenuRouteVO route = new MenuRouteVO();
            // 路由 name 优先用持久化字段；缺失时回退到 path 派生
            route.setName(notBlank(m.getRouteName()) ? m.getRouteName() : toRouteName(m.getPath()));
            route.setPath(m.getPath());
            if (m.getMenuType() != null && m.getMenuType() == MENU_TYPE_DIR) {
                route.setComponent(blankToDefault(m.getComponent(), LAYOUT_BASIC));
            } else {
                route.setComponent(m.getComponent());
            }
            route.setMeta(toMetaVO(m));
            route.setChildren(buildRouteTree(m.getId(), grouped));
            result.add(route);
        }
        return result;
    }

    // ===== 管理后台 CRUD =====

    @Override
    public List<MenuTreeVO> tree() {
        // 管理视图：全量返回（含按钮、含禁用，前端自行展示）
        List<SysMenu> menus = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));

        Map<Long, List<SysMenu>> grouped = new HashMap<>();
        for (SysMenu m : menus) {
            grouped.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(),
                    k -> new ArrayList<>()).add(m);
        }
        return buildManageTree(0L, grouped);
    }

    private List<MenuTreeVO> buildManageTree(Long parentId, Map<Long, List<SysMenu>> grouped) {
        List<SysMenu> children = grouped.get(parentId);
        if (children == null || children.isEmpty()) {
            return null;
        }
        children.sort(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()));

        List<MenuTreeVO> result = new ArrayList<>(children.size());
        for (SysMenu m : children) {
            MenuTreeVO node = toTreeVO(m);
            node.setChildren(buildManageTree(m.getId(), grouped));
            result.add(node);
        }
        return result;
    }

    @Override
    public Long create(MenuCreateRequest req) {
        Integer typeCode = MenuTypeEnum.typeToCode(req.getType());
        if (typeCode == null) {
            throw new BizException(SystemResultCode.MENU_TYPE_INVALID);
        }
        validateParent(req.getPid());
        if (notBlank(req.getName()) && existsByName(req.getName(), null)) {
            throw new BizException(SystemResultCode.MENU_NAME_EXISTS);
        }
        if (notBlank(req.getPath()) && existsByPath(req.getPath(), null)) {
            throw new BizException(SystemResultCode.MENU_PATH_EXISTS);
        }

        SysMenu entity = new SysMenu();
        entity.setMenuType(typeCode);
        entity.setParentId(req.getPid() == null ? 0L : req.getPid());
        entity.setRouteName(blankToNull(req.getName()));
        entity.setPath(blankToNull(req.getPath()));
        entity.setComponent(blankToNull(req.getComponent()));
        entity.setPerms(blankToNull(req.getAuthCode()));
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        entity.setActivePath(blankToNull(req.getActivePath()));
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        entity.setRemark(blankToNull(req.getRemark()));
        entity.setVisible(1);
        applyMeta(entity, req.getMeta(), req.getType());
        menuMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, MenuUpdateRequest req) {
        SysMenu current = menuMapper.selectById(id);
        if (current == null) {
            throw new BizException(SystemResultCode.MENU_NOT_FOUND);
        }
        // 类型若上送，必须合法
        Integer typeCode = current.getMenuType();
        String typeStr = MenuTypeEnum.codeToType(current.getMenuType());
        if (req.getType() != null) {
            Integer t = MenuTypeEnum.typeToCode(req.getType());
            if (t == null) {
                throw new BizException(SystemResultCode.MENU_TYPE_INVALID);
            }
            typeCode = t;
            typeStr = req.getType();
        }
        if (req.getPid() != null) {
            if (req.getPid().equals(id)) {
                throw new BizException(SystemResultCode.MENU_PARENT_INVALID);
            }
            if (req.getPid() != 0L) {
                validateParent(req.getPid());
            }
        }
        if (notBlank(req.getName())
                && !req.getName().equals(current.getRouteName())
                && existsByName(req.getName(), id)) {
            throw new BizException(SystemResultCode.MENU_NAME_EXISTS);
        }
        if (notBlank(req.getPath())
                && !req.getPath().equals(current.getPath())
                && existsByPath(req.getPath(), id)) {
            throw new BizException(SystemResultCode.MENU_PATH_EXISTS);
        }

        SysMenu patch = new SysMenu();
        patch.setId(id);
        if (req.getPid() != null) patch.setParentId(req.getPid());
        if (req.getType() != null) patch.setMenuType(typeCode);
        if (req.getName() != null) patch.setRouteName(blankToNull(req.getName()));
        if (req.getPath() != null) patch.setPath(blankToNull(req.getPath()));
        if (req.getComponent() != null) patch.setComponent(blankToNull(req.getComponent()));
        if (req.getAuthCode() != null) patch.setPerms(blankToNull(req.getAuthCode()));
        if (req.getStatus() != null) patch.setStatus(req.getStatus());
        if (req.getActivePath() != null) patch.setActivePath(blankToNull(req.getActivePath()));
        if (req.getSort() != null) patch.setSort(req.getSort());
        if (req.getRemark() != null) patch.setRemark(blankToNull(req.getRemark()));
        if (req.getMeta() != null) {
            applyMeta(patch, req.getMeta(), typeStr);
        }
        menuMapper.updateById(patch);
    }

    @Override
    public void delete(Long id) {
        SysMenu current = menuMapper.selectById(id);
        if (current == null) {
            throw new BizException(SystemResultCode.MENU_NOT_FOUND);
        }
        Long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(SystemResultCode.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        if (!notBlank(name)) {
            return false;
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getRouteName, name);
        if (excludeId != null) {
            wrapper.ne(SysMenu::getId, excludeId);
        }
        Long count = menuMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByPath(String path, Long excludeId) {
        if (!notBlank(path)) {
            return false;
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPath, path);
        if (excludeId != null) {
            wrapper.ne(SysMenu::getId, excludeId);
        }
        Long count = menuMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    // ===== 辅助方法 =====

    private void validateParent(Long pid) {
        if (pid == null || pid == 0L) {
            return;
        }
        SysMenu parent = menuMapper.selectById(pid);
        if (parent == null) {
            throw new BizException(SystemResultCode.MENU_PARENT_INVALID);
        }
        // 按钮类型不允许做父节点
        if (parent.getMenuType() != null && parent.getMenuType() == MenuTypeEnum.BUTTON.getCode()) {
            throw new BizException(SystemResultCode.MENU_PARENT_INVALID);
        }
    }

    /**
     * meta 入参 -> 落库字段；type 用于决定 link_url 来源（link.link 或 embedded.iframeSrc）
     */
    private void applyMeta(SysMenu entity, MenuMetaVO meta, String typeStr) {
        if (meta == null) {
            return;
        }
        // title 落 menu_name
        if (meta.getTitle() != null) {
            entity.setMenuName(meta.getTitle());
        }
        if (meta.getIcon() != null) entity.setIcon(blankToNull(meta.getIcon()));
        if (meta.getActiveIcon() != null) entity.setActiveIcon(blankToNull(meta.getActiveIcon()));
        if (meta.getOrder() != null) entity.setSort(meta.getOrder());
        if (meta.getKeepAlive() != null) entity.setKeepAlive(meta.getKeepAlive() ? 1 : 0);
        if (meta.getAffixTab() != null) entity.setAffixTab(meta.getAffixTab() ? 1 : 0);
        if (meta.getHideInMenu() != null) entity.setHideInMenu(meta.getHideInMenu() ? 1 : 0);
        if (meta.getHideChildrenInMenu() != null) entity.setHideChildrenInMenu(meta.getHideChildrenInMenu() ? 1 : 0);
        if (meta.getHideInBreadcrumb() != null) entity.setHideInBreadcrumb(meta.getHideInBreadcrumb() ? 1 : 0);
        if (meta.getHideInTab() != null) entity.setHideInTab(meta.getHideInTab() ? 1 : 0);
        if (meta.getActivePath() != null) entity.setActivePath(blankToNull(meta.getActivePath()));
        if (meta.getBadgeType() != null) entity.setBadgeType(blankToNull(meta.getBadgeType()));
        if (meta.getBadge() != null) entity.setBadge(blankToNull(meta.getBadge()));
        if (meta.getBadgeVariants() != null) entity.setBadgeVariants(blankToNull(meta.getBadgeVariants()));

        // link / iframeSrc 共用 link_url 列，按类型决定
        if (MenuTypeEnum.LINK.getType().equals(typeStr) && meta.getLink() != null) {
            entity.setLinkUrl(blankToNull(meta.getLink()));
        } else if (MenuTypeEnum.EMBEDDED.getType().equals(typeStr) && meta.getIframeSrc() != null) {
            entity.setLinkUrl(blankToNull(meta.getIframeSrc()));
        }
    }

    private MenuMetaVO toMetaVO(SysMenu m) {
        MenuMetaVO meta = new MenuMetaVO();
        meta.setTitle(m.getMenuName());
        meta.setIcon(blankToNull(m.getIcon()));
        meta.setActiveIcon(blankToNull(m.getActiveIcon()));
        meta.setOrder(m.getSort());
        meta.setKeepAlive(toBool(m.getKeepAlive()));
        meta.setAffixTab(toBool(m.getAffixTab()));
        meta.setHideInMenu(toBool(m.getHideInMenu()));
        meta.setHideChildrenInMenu(toBool(m.getHideChildrenInMenu()));
        meta.setHideInBreadcrumb(toBool(m.getHideInBreadcrumb()));
        meta.setHideInTab(toBool(m.getHideInTab()));
        meta.setActivePath(blankToNull(m.getActivePath()));
        meta.setBadgeType(blankToNull(m.getBadgeType()));
        meta.setBadge(blankToNull(m.getBadge()));
        meta.setBadgeVariants(blankToNull(m.getBadgeVariants()));
        if (m.getMenuType() != null && m.getMenuType() == MenuTypeEnum.LINK.getCode()) {
            meta.setLink(blankToNull(m.getLinkUrl()));
        } else if (m.getMenuType() != null && m.getMenuType() == MenuTypeEnum.EMBEDDED.getCode()) {
            meta.setIframeSrc(blankToNull(m.getLinkUrl()));
        }
        return meta;
    }

    private MenuTreeVO toTreeVO(SysMenu m) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(m.getId());
        vo.setPid(m.getParentId());
        vo.setName(blankToNull(m.getRouteName()));
        vo.setType(MenuTypeEnum.codeToType(m.getMenuType()));
        vo.setPath(blankToNull(m.getPath()));
        vo.setComponent(blankToNull(m.getComponent()));
        vo.setAuthCode(blankToNull(m.getPerms()));
        vo.setStatus(m.getStatus());
        vo.setActivePath(blankToNull(m.getActivePath()));
        vo.setSort(m.getSort());
        vo.setRemark(blankToNull(m.getRemark()));
        vo.setCreateTime(m.getCreateTime());
        vo.setUpdateTime(m.getUpdateTime());
        vo.setMeta(toMetaVO(m));
        return vo;
    }

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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static Boolean toBool(Integer v) {
        if (v == null) return null;
        return v != 0;
    }
}
