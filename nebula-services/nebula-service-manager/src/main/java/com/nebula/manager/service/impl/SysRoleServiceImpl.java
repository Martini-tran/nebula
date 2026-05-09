package com.nebula.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.RoleCreateRequest;
import com.nebula.manager.dto.RolePageQuery;
import com.nebula.manager.dto.RoleUpdateRequest;
import com.nebula.manager.enums.ManagerResultCode;
import com.nebula.manager.mapper.SysRoleMapper;
import com.nebula.manager.mapper.SysRoleMenuMapper;
import com.nebula.manager.mapper.SysUserMapper;
import com.nebula.manager.mapper.SysUserRoleMapper;
import com.nebula.manager.service.SysRoleService;
import com.nebula.manager.vo.RoleListVO;
import com.nebula.manager.vo.RoleSimpleVO;
import com.nebula.system.entity.SysRole;
import com.nebula.system.entity.SysRoleMenu;
import com.nebula.system.entity.SysUser;
import com.nebula.system.entity.SysUserRole;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统角色服务实现类
 * 提供角色的增删改查、权限分配、用户分配等功能
 *
 * @author nebula
 */
@Slf4j
@Service
public class SysRoleServiceImpl implements SysRoleService {

    /**
     * 角色编码正则表达式
     * 角色编码仅允许字母/数字/下划线/短横线，长度2-50
     */
    private static final String ROLE_CODE_REGEX = "^[A-Za-z0-9_-]{2,50}$";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;

    public SysRoleServiceImpl(SysRoleMapper roleMapper,
                              SysRoleMenuMapper roleMenuMapper,
                              SysUserRoleMapper userRoleMapper,
                              SysUserMapper userMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
    }

    /**
     * 分页查询角色列表
     * 根据查询条件分页返回角色列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<RoleListVO> page(RolePageQuery query) {
        log.info("开始分页查询角色列表，页码: {}, 页大小: {}", query.safePageNum(), query.safePageSize());

        Page<SysRole> page = new Page<>(query.safePageNum(), query.safePageSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(notBlank(query.getRoleCode()), SysRole::getRoleCode, query.getRoleCode())
                .like(notBlank(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByDesc(SysRole::getCreateTime);
        Page<SysRole> result = roleMapper.selectPage(page, wrapper);
        List<RoleListVO> rows = result.getRecords().stream().map(this::toListVO).toList();

        log.info("角色列表分页查询完成，总记录数: {}, 返回记录数: {}", result.getTotal(), rows.size());
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取启用的角色列表
     * 查询所有状态为启用的角色
     *
     * @return 启用的角色列表
     */
    @Override
    public List<RoleSimpleVO> listEnabled() {
        log.info("开始获取启用的角色列表");

        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));

        List<RoleSimpleVO> result = roles.stream().map(r -> {
            RoleSimpleVO vo = new RoleSimpleVO();
            vo.setId(r.getId());
            vo.setRoleCode(r.getRoleCode());
            vo.setRoleName(r.getRoleName());
            return vo;
        }).toList();

        log.info("获取启用的角色列表完成，共{}条记录", result.size());
        return result;
    }

    /**
     * 获取角色详情
     * 根据ID获取角色详细信息
     *
     * @param id 角色ID
     * @return 角色详情
     */
    @Override
    public RoleListVO detail(Long id) {
        log.info("开始获取角色详情，ID: {}", id);

        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            log.warn("角色不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }

        RoleListVO result = toListVO(role);
        log.info("角色详情获取成功，ID: {}", id);
        return result;
    }

    /**
     * 创建新角色
     * 根据请求参数创建新的角色记录
     *
     * @param req 创建请求参数
     * @return 新创建角色的ID
     */
    @Override
    public Long create(RoleCreateRequest req) {
        log.info("开始创建角色，编码: {}, 名称: {}", req.getRoleCode(), req.getRoleName());

        validateCode(req.getRoleCode());
        if (!notBlank(req.getRoleName())) {
            log.warn("角色名称不能为空");
            throw new BizException(ManagerResultCode.ROLE_NAME_EXISTS.getCode(), "请输入角色名称");
        }
        if (existsByCode(req.getRoleCode(), null)) {
            log.warn("角色编码已存在: {}", req.getRoleCode());
            throw new BizException(ManagerResultCode.ROLE_CODE_EXISTS);
        }
        if (existsByName(req.getRoleName(), null)) {
            log.warn("角色名称已存在: {}", req.getRoleName());
            throw new BizException(ManagerResultCode.ROLE_NAME_EXISTS);
        }

        SysRole entity = new SysRole();
        entity.setRoleCode(req.getRoleCode());
        entity.setRoleName(req.getRoleName());
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        entity.setRemark(blankToNull(req.getRemark()));

        int insertCount = roleMapper.insert(entity);
        log.info("角色创建成功，ID: {}，影响行数: {}", entity.getId(), insertCount);
        return entity.getId();
    }

    /**
     * 更新角色信息
     * 根据ID和请求参数更新角色记录
     *
     * @param id  角色ID
     * @param req 更新请求参数
     */
    @Override
    public void update(Long id, RoleUpdateRequest req) {
        log.info("开始更新角色，ID: {}", id);

        SysRole current = roleMapper.selectById(id);
        if (current == null) {
            log.warn("角色不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        if (SecurityConstants.ROLE_SUPER_ADMIN.equals(current.getRoleCode())) {
            log.warn("超级管理员角色不允许修改，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_SUPER_ADMIN_FORBIDDEN);
        }
        if (notBlank(req.getRoleName())
                && !req.getRoleName().equals(current.getRoleName())
                && existsByName(req.getRoleName(), id)) {
            log.warn("角色名称已存在: {}", req.getRoleName());
            throw new BizException(ManagerResultCode.ROLE_NAME_EXISTS);
        }

        SysRole patch = new SysRole();
        patch.setId(id);
        if (req.getRoleName() != null) patch.setRoleName(req.getRoleName());
        if (req.getStatus() != null) patch.setStatus(req.getStatus());
        if (req.getRemark() != null) patch.setRemark(blankToNull(req.getRemark()));

        int updateCount = roleMapper.updateById(patch);
        log.info("角色更新成功，ID: {}，影响行数: {}", id, updateCount);
    }

    /**
     * 删除角色
     * 根据ID软删除角色，需确保该角色没有绑定用户
     *
     * @param id 角色ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("开始删除角色，ID: {}", id);

        SysRole current = roleMapper.selectById(id);
        if (current == null) {
            log.warn("角色不存在，无法删除，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        if (SecurityConstants.ROLE_SUPER_ADMIN.equals(current.getRoleCode())) {
            log.warn("超级管理员角色不允许删除，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_SUPER_ADMIN_FORBIDDEN);
        }

        // 检查是否有用户绑定此角色
        Long boundUsers = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (boundUsers != null && boundUsers > 0) {
            log.warn("角色存在绑定用户，无法删除，ID: {}，绑定用户数: {}", id, boundUsers);
            throw new BizException(ManagerResultCode.ROLE_HAS_USERS);
        }

        // 删除角色菜单关联
        int deleteRoleMenuCount = roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, id));

        // 软删除角色
        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setDeleteTime(LocalDateTime.now());
        int updateCount = roleMapper.updateById(patch);

        // 物理删除角色
        int deleteCount = roleMapper.deleteById(id);

        log.info("角色删除成功，ID: {}，删除角色菜单关联数: {}，更新行数: {}，物理删除行数: {}",
                id, deleteRoleMenuCount, updateCount, deleteCount);
    }

    /**
     * 更新角色状态
     * 更新角色的启用/禁用状态
     *
     * @param id     角色ID
     * @param status 状态值（1启用，0禁用）
     */
    @Override
    public void updateStatus(Long id, Integer status) {
        log.info("开始更新角色状态，ID: {}，状态: {}", id, status);

        if (status == null || (status != 0 && status != 1)) {
            log.warn("角色状态值非法: {}", status);
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND.getCode(), "状态值非法");
        }

        SysRole current = roleMapper.selectById(id);
        if (current == null) {
            log.warn("角色不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        if (SecurityConstants.ROLE_SUPER_ADMIN.equals(current.getRoleCode())) {
            log.warn("超级管理员角色不允许修改状态，ID: {}", id);
            throw new BizException(ManagerResultCode.ROLE_SUPER_ADMIN_FORBIDDEN);
        }

        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setStatus(status);

        int updateCount = roleMapper.updateById(patch);
        log.info("角色状态更新成功，ID: {}，影响行数: {}", id, updateCount);
    }

    /**
     * 获取角色关联的菜单ID列表
     * 查询指定角色绑定的所有菜单ID
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    @Override
    public List<Long> listMenuIds(Long roleId) {
        log.info("开始获取角色菜单ID列表，角色ID: {}", roleId);

        ensureRoleExists(roleId);
        List<SysRoleMenu> rows = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));

        List<Long> result = rows.stream().map(SysRoleMenu::getMenuId).toList();
        log.info("获取角色菜单ID列表完成，角色ID: {}，菜单ID数量: {}", roleId, result.size());
        return result;
    }

    /**
     * 为角色分配菜单权限
     * 将指定的菜单ID列表分配给角色
     *
     * @param roleId   角色ID
     * @param menuIds  菜单ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        log.info("开始为角色分配菜单权限，角色ID: {}，菜单数量: {}", roleId, menuIds != null ? menuIds.size() : 0);

        ensureRoleExists(roleId);

        // 清除原有角色菜单关联
        int deleteCount = roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));

        if (menuIds == null || menuIds.isEmpty()) {
            log.info("角色菜单权限分配完成，清除原有关联数: {}", deleteCount);
            return;
        }

        // 去重处理
        Set<Long> uniq = new HashSet<>(menuIds);
        for (Long menuId : uniq) {
            if (menuId == null) continue;
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }

        log.info("角色菜单权限分配完成，角色ID: {}，新增关联数: {}，清除原有关联数: {}",
                roleId, uniq.size(), deleteCount);
    }

    /**
     * 获取角色关联的用户ID列表
     * 查询指定角色绑定的所有用户ID
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    @Override
    public List<Long> listUserIds(Long roleId) {
        log.info("开始获取角色用户ID列表，角色ID: {}", roleId);

        ensureRoleExists(roleId);
        List<SysUserRole> rows = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = rows.stream().map(SysUserRole::getUserId).toList();
        // 过滤掉已被删除的用户
        Set<Long> existingIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .select(SysUser::getId))
                .stream().map(SysUser::getId).collect(Collectors.toSet());

        List<Long> result = userIds.stream().filter(existingIds::contains).toList();
        log.info("获取角色用户ID列表完成，角色ID: {}，用户ID数量: {}", roleId, result.size());
        return result;
    }

    /**
     * 为角色分配用户
     * 将指定的用户ID列表分配给角色
     *
     * @param roleId  角色ID
     * @param userIds 用户ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(Long roleId, List<Long> userIds) {
        log.info("开始为角色分配用户，角色ID: {}，用户数量: {}", roleId, userIds != null ? userIds.size() : 0);

        ensureRoleExists(roleId);

        SysRole role = roleMapper.selectById(roleId);
        if (SecurityConstants.ROLE_SUPER_ADMIN.equals(role.getRoleCode())) {
            log.warn("超级管理员角色不允许分配用户，roleId: {}", roleId);
            throw new BizException(ManagerResultCode.ROLE_SUPER_ADMIN_FORBIDDEN);
        }

        // 查询现有用户角色关联
        List<SysUserRole> existing = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        Set<Long> existingIds = existing.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
        Set<Long> targetIds = userIds == null ? Collections.emptySet() : new HashSet<>(userIds);
        targetIds.remove(null);

        // 计算需要删除的关联
        Set<Long> toRemove = new HashSet<>(existingIds);
        toRemove.removeAll(targetIds);
        if (!toRemove.isEmpty()) {
            int removeCount = userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, roleId)
                    .in(SysUserRole::getUserId, toRemove));
            log.debug("移除用户角色关联数: {}", removeCount);
        }

        // 计算需要添加的关联
        Set<Long> toAdd = new HashSet<>(targetIds);
        toAdd.removeAll(existingIds);
        for (Long userId : toAdd) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }

        log.info("角色用户分配完成，角色ID: {}，新增用户数: {}，移除用户数: {}",
                roleId, toAdd.size(), toRemove.size());
    }

    /**
     * 确保角色存在
     * 检查角色是否存在，不存在则抛出异常
     *
     * @param roleId 角色ID
     */
    private void ensureRoleExists(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("角色不存在，ID: {}", roleId);
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
    }

    /**
     * 验证角色编码格式
     * 角色编码仅允许字母/数字/下划线/短横线，长度2-50
     *
     * @param code 角色编码
     */
    private void validateCode(String code) {
        if (!notBlank(code)) {
            log.warn("角色编码不能为空");
            throw new BizException(ManagerResultCode.ROLE_CODE_INVALID, "请输入角色编码");
        }
        if (!code.matches(ROLE_CODE_REGEX)) {
            log.warn("角色编码格式不合法: {}", code);
            throw new BizException(ManagerResultCode.ROLE_CODE_INVALID,
                    "角色编码仅允许字母 / 数字 / 下划线 / 短横线，长度 2-50");
        }
    }

    /**
     * 检查角色编码是否存在
     *
     * @param code      角色编码
     * @param excludeId 排除的角色ID（用于更新时检查）
     * @return 是否存在
     */
    private boolean existsByCode(String code, Long excludeId) {
        if (!notBlank(code)) return false;
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code);
        if (excludeId != null) wrapper.ne(SysRole::getId, excludeId);
        Long count = roleMapper.selectCount(wrapper);
        boolean exists = count != null && count > 0;
        log.debug("检查角色编码是否存在: {}，结果: {}", code, exists);
        return exists;
    }

    /**
     * 检查角色名称是否存在
     *
     * @param name      角色名称
     * @param excludeId 排除的角色ID（用于更新时检查）
     * @return 是否存在
     */
    private boolean existsByName(String name, Long excludeId) {
        if (!notBlank(name)) return false;
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleName, name);
        if (excludeId != null) wrapper.ne(SysRole::getId, excludeId);
        Long count = roleMapper.selectCount(wrapper);
        boolean exists = count != null && count > 0;
        log.debug("检查角色名称是否存在: {}，结果: {}", name, exists);
        return exists;
    }

    /**
     * 将角色实体转换为列表视图对象
     *
     * @param role 角色实体
     * @return 列表视图对象
     */
    private RoleListVO toListVO(SysRole role) {
        RoleListVO vo = new RoleListVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
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
     * 空值转null
     *
     * @param s 原值
     * @return 转换后的值
     */
    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}
