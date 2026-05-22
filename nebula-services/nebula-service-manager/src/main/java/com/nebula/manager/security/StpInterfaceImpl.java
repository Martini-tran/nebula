package com.nebula.manager.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.manager.mapper.SysMenuMapper;
import com.nebula.manager.mapper.SysRoleMapper;
import com.nebula.manager.mapper.SysRoleMenuMapper;
import com.nebula.manager.mapper.SysUserRoleMapper;
import com.nebula.system.entity.SysMenu;
import com.nebula.system.entity.SysRole;
import com.nebula.system.entity.SysRoleMenu;
import com.nebula.system.entity.SysUserRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 权限/角色来源实现
 * 由 Sa-Token 在 {@code StpUtil.hasPermission} / {@code StpUtil.hasRole} 等校验时回调；
 * 角色编码取自 {@code sys_role.role_code}，权限编码取自 {@code sys_menu.perms}。
 * 拥有超级管理员角色的用户直接返回 "*" 视为拥有全部权限。
 *
 * @author nebula
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 通配符权限：Sa-Token 内置语义，等价于拥有任意权限标识
     */
    private static final String WILDCARD_PERMISSION = "*";

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;

    public StpInterfaceImpl(SysUserRoleMapper userRoleMapper,
                            SysRoleMapper roleMapper,
                            SysRoleMenuMapper roleMenuMapper,
                            SysMenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    private void syncSessionValue(Object loginId, String key, List<String> value) {
        try {
            StpUtil.getSessionByLoginId(loginId).set(key, value);
        } catch (Exception e) {
            log.warn("Sync Sa-Token session auth data failed, loginId: {}, key: {}, msg: {}",
                    loginId, key, e.getMessage());
        }
    }

    /**
     * 返回当前账号拥有的权限标识列表
     * 超级管理员返回通配符 "*"，普通用户返回菜单 perms 去重列表
     *
     * @param loginId   Sa-Token 登录ID（这里即用户ID）
     * @param loginType 账号体系标识，单体系下未使用
     * @return 权限编码列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }

        List<SysRole> roles = loadEnabledRoles(userId);
        if (roles.isEmpty()) {
            syncSessionValue(loginId, SaSession.PERMISSION_LIST, Collections.emptyList());
            return Collections.emptyList();
        }
        if (containsSuperAdmin(roles)) {
            List<String> permissions = List.of(WILDCARD_PERMISSION);
            syncSessionValue(loginId, SaSession.PERMISSION_LIST, permissions);
            return permissions;
        }

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<SysRoleMenu> roleMenus = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .in(SysRoleMenu::getRoleId, roleIds));
        if (roleMenus.isEmpty()) {
            syncSessionValue(loginId, SaSession.PERMISSION_LIST, Collections.emptyList());
            return Collections.emptyList();
        }

        Set<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, 1));

        List<String> perms = menus.stream()
                .map(SysMenu::getPerms)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        syncSessionValue(loginId, SaSession.PERMISSION_LIST, perms);
        log.debug("用户权限解析完成，userId: {}, 权限数: {}", userId, perms.size());
        return perms;
    }

    /**
     * 返回当前账号拥有的角色编码列表
     *
     * @param loginId   Sa-Token 登录ID（这里即用户ID）
     * @param loginType 账号体系标识，单体系下未使用
     * @return 角色编码列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        List<SysRole> roles = loadEnabledRoles(userId);
        List<String> codes = roles.stream()
                .map(SysRole::getRoleCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
        syncSessionValue(loginId, SaSession.ROLE_LIST, codes);
        log.debug("用户角色解析完成，userId: {}, 角色: {}", userId, codes);
        return codes;
    }

    /**
     * 加载用户启用状态的角色列表
     *
     * @param userId 用户ID
     * @return 启用角色集合
     */
    private List<SysRole> loadEnabledRoles(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 1));
    }

    /**
     * 判断角色集合中是否包含超级管理员
     */
    private boolean containsSuperAdmin(List<SysRole> roles) {
        return roles.stream()
                .anyMatch(r -> SecurityConstants.ROLE_SUPER_ADMIN.equals(r.getRoleCode()));
    }

    /**
     * 解析 Sa-Token loginId 为用户ID
     * 兼容 Long / Integer / 数字字符串
     */
    private Long parseUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(loginId.toString());
        } catch (NumberFormatException e) {
            log.warn("Sa-Token loginId 非数字，无法作为用户ID解析: {}", loginId);
            return null;
        }
    }
}
