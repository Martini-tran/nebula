package com.nebula.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.MenuCreateRequest;
import com.nebula.manager.dto.MenuUpdateRequest;
import com.nebula.manager.enums.ManagerResultCode;
import com.nebula.manager.enums.MenuTypeEnum;
import com.nebula.manager.mapper.SysMenuMapper;
import com.nebula.manager.mapper.SysRoleMapper;
import com.nebula.manager.mapper.SysRoleMenuMapper;
import com.nebula.manager.service.SysMenuService;
import com.nebula.manager.vo.MenuMetaVO;
import com.nebula.manager.vo.MenuRouteVO;
import com.nebula.manager.vo.MenuTreeVO;
import com.nebula.system.entity.SysMenu;
import com.nebula.system.entity.SysRole;
import com.nebula.system.entity.SysRoleMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统菜单服务实现类
 * 提供菜单的增删改查、路由构建、树形结构构建等功能
 *
 * @author nebula
 */
@Slf4j
@Service
public class SysMenuServiceImpl implements SysMenuService {

    /**
     * 目录类型常量
     */
    private static final int MENU_TYPE_DIR = 1;

    /**
     * 菜单类型常量
     */
    private static final int MENU_TYPE_MENU = 2;

    /**
     * 基础布局组件名常量
     */
    private static final String LAYOUT_BASIC = "BasicLayout";

    /**
     * 菜单数据访问层
     */
    private final SysMenuMapper menuMapper;

    /**
     * 角色数据访问层
     */
    private final SysRoleMapper roleMapper;

    /**
     * 角色菜单关系数据访问层
     */
    private final SysRoleMenuMapper roleMenuMapper;

    public SysMenuServiceImpl(SysMenuMapper menuMapper,
                              SysRoleMapper roleMapper,
                              SysRoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    /**
     * 获取菜单路由列表
     * 查询所有可见的目录和菜单类型的菜单项，并构建成树形路由结构
     *
     * @return 路由列表
     */
    @Override
    public List<MenuRouteVO> listRoutes() {
        log.info("开始获取菜单路由列表");

        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getStatus, 1) // 状态为启用
                .eq(SysMenu::getVisible, 1) // 可见
                .in(SysMenu::getMenuType, MENU_TYPE_DIR, MENU_TYPE_MENU) // 目录或菜单类型
                .orderByAsc(SysMenu::getSort); // 按排序字段升序
        List<SysMenu> menus = menuMapper.selectList(wrapper);

        // 按父ID分组菜单数据
        Map<Long, List<SysMenu>> grouped = new HashMap<>();
        for (SysMenu m : menus) {
            grouped.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(),
                    k -> new ArrayList<>()).add(m);
        }

        List<MenuRouteVO> routes = buildRouteTree(0L, grouped);
        log.info("成功获取菜单路由列表，共{}个根节点", routes != null ? routes.size() : 0);
        return routes;
    }

    /**
     * 构建路由树形结构
     * 递归构建从指定父ID开始的菜单路由树
     *
     * @param parentId 父菜单ID
     * @param grouped  已按父ID分组的菜单数据
     * @return 路由树列表
     */
    private List<MenuRouteVO> buildRouteTree(Long parentId, Map<Long, List<SysMenu>> grouped) {
        List<SysMenu> children = grouped.get(parentId);
        if (children == null || children.isEmpty()) {
            return null;
        }
        // 按排序字段排序
        children.sort(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()));

        List<MenuRouteVO> result = new ArrayList<>(children.size());
        for (SysMenu m : children) {
            MenuRouteVO route = new MenuRouteVO();
            // 设置路由名称，如果为空则根据路径生成
            route.setName(notBlank(m.getRouteName()) ? m.getRouteName() : toRouteName(m.getPath()));
            route.setPath(m.getPath());

            // 根据菜单类型设置组件
            if (m.getMenuType() != null && m.getMenuType() == MENU_TYPE_DIR) {
                route.setComponent(blankToDefault(m.getComponent(), LAYOUT_BASIC));
            } else {
                route.setComponent(m.getComponent());
            }
            route.setMeta(toMetaVO(m)); // 设置元数据
            route.setChildren(buildRouteTree(m.getId(), grouped)); // 递归构建子菜单
            result.add(route);
        }
        return result;
    }

    /**
     * 获取菜单管理树
     * 查询所有菜单并构建成树形结构，用于管理界面展示
     *
     * @return 菜单树列表
     */
    @Override
    public List<MenuTreeVO> tree() {
        log.info("开始获取菜单管理树");

        List<SysMenu> menus = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));

        // 按父ID分组菜单数据
        Map<Long, List<SysMenu>> grouped = new HashMap<>();
        for (SysMenu m : menus) {
            grouped.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(),
                    k -> new ArrayList<>()).add(m);
        }

        List<MenuTreeVO> tree = buildManageTree(0L, grouped);
        log.info("成功获取菜单管理树，共{}个根节点", tree != null ? tree.size() : 0);
        return tree;
    }

    /**
     * 构建管理树形结构
     * 递归构建从指定父ID开始的菜单管理树
     *
     * @param parentId 父菜单ID
     * @param grouped  已按父ID分组的菜单数据
     * @return 管理树列表
     */
    private List<MenuTreeVO> buildManageTree(Long parentId, Map<Long, List<SysMenu>> grouped) {
        List<SysMenu> children = grouped.get(parentId);
        if (children == null || children.isEmpty()) {
            return null;
        }
        // 按排序字段排序
        children.sort(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()));

        List<MenuTreeVO> result = new ArrayList<>(children.size());
        for (SysMenu m : children) {
            MenuTreeVO node = toTreeVO(m); // 转换为树节点对象
            node.setChildren(buildManageTree(m.getId(), grouped)); // 递归构建子菜单
            result.add(node);
        }
        return result;
    }

    /**
     * 创建新菜单
     * 根据请求参数创建新的菜单记录
     *
     * @param req 创建请求参数
     * @return 新创建菜单的ID
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long create(MenuCreateRequest req) {
        log.info("开始创建菜单，名称: {}, 路径: {}", req.getName(), req.getPath());

        // 验证菜单类型
        Integer typeCode = MenuTypeEnum.typeToCode(req.getType());
        if (typeCode == null) {
            log.warn("菜单类型无效: {}", req.getType());
            throw new BizException(ManagerResultCode.MENU_TYPE_INVALID);
        }

        // 验证父菜单
        validateParent(req.getPid());

        // 检查名称是否重复
        if (notBlank(req.getName()) && existsByName(req.getName(), null)) {
            log.warn("菜单名称已存在: {}", req.getName());
            throw new BizException(ManagerResultCode.MENU_NAME_EXISTS);
        }

        // 检查路径是否重复
        if (notBlank(req.getPath()) && existsByPath(req.getPath(), null)) {
            log.warn("菜单路径已存在: {}", req.getPath());
            throw new BizException(ManagerResultCode.MENU_PATH_EXISTS);
        }

        // 构建菜单实体
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
        applyMeta(entity, req.getMeta(), req.getType()); // 应用元数据

        int insertCount = menuMapper.insert(entity);
        log.info("菜单创建成功，ID: {}，影响行数: {}", entity.getId(), insertCount);

        assignMenuToSuperAdmin(entity.getId());

        return entity.getId();
    }

    /**
     * 更新菜单信息
     * 根据ID和请求参数更新菜单记录
     *
     * @param id  菜单ID
     * @param req 更新请求参数
     */
    @Override
    public void update(Long id, MenuUpdateRequest req) {
        log.info("开始更新菜单，ID: {}", id);

        // 查询当前菜单记录
        SysMenu current = menuMapper.selectById(id);
        if (current == null) {
            log.warn("菜单不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.MENU_NOT_FOUND);
        }

        Integer typeCode = current.getMenuType();
        String typeStr = MenuTypeEnum.codeToType(current.getMenuType());

        // 处理类型变更
        if (req.getType() != null) {
            Integer t = MenuTypeEnum.typeToCode(req.getType());
            if (t == null) {
                log.warn("菜单类型无效: {}", req.getType());
                throw new BizException(ManagerResultCode.MENU_TYPE_INVALID);
            }
            typeCode = t;
            typeStr = req.getType();
        }

        // 验证父菜单ID
        if (req.getPid() != null) {
            if (req.getPid().equals(id)) {
                log.warn("不能将菜单设为自己父菜单，ID: {}", id);
                throw new BizException(ManagerResultCode.MENU_PARENT_INVALID);
            }
            if (req.getPid() != 0L) {
                validateParent(req.getPid());
            }
        }

        // 检查名称是否重复
        if (notBlank(req.getName())
                && !req.getName().equals(current.getRouteName())
                && existsByName(req.getName(), id)) {
            log.warn("菜单名称已存在: {}", req.getName());
            throw new BizException(ManagerResultCode.MENU_NAME_EXISTS);
        }

        // 检查路径是否重复
        if (notBlank(req.getPath())
                && !req.getPath().equals(current.getPath())
                && existsByPath(req.getPath(), id)) {
            log.warn("菜单路径已存在: {}", req.getPath());
            throw new BizException(ManagerResultCode.MENU_PATH_EXISTS);
        }

        // 构建更新实体
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

        int updateCount = menuMapper.updateById(patch);
        log.info("菜单更新成功，ID: {}，影响行数: {}", id, updateCount);
    }

    /**
     * 删除菜单
     * 根据ID删除菜单，需确保该菜单没有子菜单
     *
     * @param id 菜单ID
     */
    @Override
    public void delete(Long id) {
        log.info("开始删除菜单，ID: {}", id);

        SysMenu current = menuMapper.selectById(id);
        if (current == null) {
            log.warn("菜单不存在，无法删除，ID: {}", id);
            throw new BizException(ManagerResultCode.MENU_NOT_FOUND);
        }

        // 检查是否有子菜单
        Long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            log.warn("菜单存在子菜单，无法删除，ID: {}，子菜单数量: {}", id, childCount);
            throw new BizException(ManagerResultCode.MENU_HAS_CHILDREN);
        }

        int deleteCount = menuMapper.deleteById(id);
        log.info("菜单删除成功，ID: {}，影响行数: {}", id, deleteCount);
    }

    /**
     * 检查菜单名称是否存在
     *
     * @param name       菜单名称
     * @param excludeId  排除的菜单ID（用于更新时检查）
     * @return 是否存在
     */
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
        boolean exists = count != null && count > 0;
        log.debug("检查菜单名称是否存在: {}，结果: {}", name, exists);
        return exists;
    }

    /**
     * 检查菜单路径是否存在
     *
     * @param path       菜单路径
     * @param excludeId  排除的菜单ID（用于更新时检查）
     * @return 是否存在
     */
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
        boolean exists = count != null && count > 0;
        log.debug("检查菜单路径是否存在: {}，结果: {}", path, exists);
        return exists;
    }

    /**
     * 将菜单关联到超级管理员角色
     */
    private void assignMenuToSuperAdmin(Long menuId) {
        SysRole superAdmin = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, SecurityConstants.ROLE_SUPER_ADMIN));
        if (superAdmin == null) {
            log.warn("超级管理员角色不存在，跳过菜单自动关联，menuId: {}", menuId);
            return;
        }
        SysRoleMenu rm = new SysRoleMenu();
        rm.setRoleId(superAdmin.getId());
        rm.setMenuId(menuId);
        roleMenuMapper.insert(rm);
        log.info("菜单已自动关联超级管理员角色，menuId: {}，roleId: {}", menuId, superAdmin.getId());
    }

    /**
     * 验证父菜单是否有效
     * 父菜单不能是按钮类型，也不能不存在
     *
     * @param pid 父菜单ID
     */
    private void validateParent(Long pid) {
        if (pid == null || pid == 0L) {
            return;
        }

        SysMenu parent = menuMapper.selectById(pid);
        if (parent == null) {
            log.warn("父菜单不存在: {}", pid);
            throw new BizException(ManagerResultCode.MENU_PARENT_INVALID);
        }

        if (parent.getMenuType() != null && parent.getMenuType() == MenuTypeEnum.BUTTON.getCode()) {
            log.warn("父菜单不能是按钮类型: {}", pid);
            throw new BizException(ManagerResultCode.MENU_PARENT_INVALID);
        }
    }

    /**
     * 应用元数据到菜单实体
     *
     * @param entity 菜单实体
     * @param meta   元数据对象
     * @param typeStr 菜单类型字符串
     */
    private void applyMeta(SysMenu entity, MenuMetaVO meta, String typeStr) {
        if (meta == null) {
            return;
        }
        if (meta.getTitle() != null) entity.setMenuName(meta.getTitle());
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
        if (MenuTypeEnum.LINK.getType().equals(typeStr) && meta.getLink() != null) {
            entity.setLinkUrl(blankToNull(meta.getLink()));
        } else if (MenuTypeEnum.EMBEDDED.getType().equals(typeStr) && meta.getIframeSrc() != null) {
            entity.setLinkUrl(blankToNull(meta.getIframeSrc()));
        }
    }

    /**
     * 将菜单实体转换为元数据对象
     *
     * @param m 菜单实体
     * @return 元数据对象
     */
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

    /**
     * 将菜单实体转换为树节点对象
     *
     * @param m 菜单实体
     * @return 树节点对象
     */
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

    /**
     * 根据路径生成路由名称
     * 将路径中的特殊字符替换为驼峰命名
     *
     * @param path 路径
     * @return 路由名称
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
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.length() == 0 ? "Root" : sb.toString();
    }

    /**
     * 空值转默认值
     *
     * @param value       原值
     * @param defaultValue 默认值
     * @return 转换后的值
     */
    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 空值转null
     *
     * @param value 原值
     * @return 转换后的值
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 检查字符串是否非空
     *
     * @param s 字符串
     * @return 是否非空
     */
    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 整型转布尔型
     *
     * @param v 整数值
     * @return 布尔值
     */
    private static Boolean toBool(Integer v) {
        if (v == null) return null;
        return v != 0;
    }
}
