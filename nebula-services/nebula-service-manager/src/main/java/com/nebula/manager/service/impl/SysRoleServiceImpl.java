package com.nebula.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.RoleCreateRequest;
import com.nebula.manager.dto.RolePageQuery;
import com.nebula.manager.dto.RoleUpdateRequest;
import com.nebula.manager.enums.ManagerResultCode;
import com.nebula.manager.mapper.SysRoleMapper;
import com.nebula.manager.mapper.SysRoleMenuMapper;
import com.nebula.manager.mapper.SysUserRoleMapper;
import com.nebula.manager.service.SysRoleService;
import com.nebula.manager.vo.RoleListVO;
import com.nebula.manager.vo.RoleSimpleVO;
import com.nebula.system.entity.SysRole;
import com.nebula.system.entity.SysRoleMenu;
import com.nebula.system.entity.SysUserRole;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysRoleServiceImpl implements SysRoleService {

    private static final String ROLE_CODE_REGEX = "^[A-Za-z0-9_-]{2,50}$";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    public SysRoleServiceImpl(SysRoleMapper roleMapper,
                              SysRoleMenuMapper roleMenuMapper,
                              SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public PageResult<RoleListVO> page(RolePageQuery query) {
        Page<SysRole> page = new Page<>(query.safePageNum(), query.safePageSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(notBlank(query.getRoleCode()), SysRole::getRoleCode, query.getRoleCode())
                .like(notBlank(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByDesc(SysRole::getCreateTime);
        Page<SysRole> result = roleMapper.selectPage(page, wrapper);
        List<RoleListVO> rows = result.getRecords().stream().map(this::toListVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<RoleSimpleVO> listEnabled() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));
        return roles.stream().map(r -> {
            RoleSimpleVO vo = new RoleSimpleVO();
            vo.setId(r.getId());
            vo.setRoleCode(r.getRoleCode());
            vo.setRoleName(r.getRoleName());
            return vo;
        }).toList();
    }

    @Override
    public RoleListVO detail(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        return toListVO(role);
    }

    @Override
    public Long create(RoleCreateRequest req) {
        validateCode(req.getRoleCode());
        if (!notBlank(req.getRoleName())) {
            throw new BizException(ManagerResultCode.ROLE_NAME_EXISTS.getCode(), "请输入角色名称");
        }
        if (existsByCode(req.getRoleCode(), null)) {
            throw new BizException(ManagerResultCode.ROLE_CODE_EXISTS);
        }
        if (existsByName(req.getRoleName(), null)) {
            throw new BizException(ManagerResultCode.ROLE_NAME_EXISTS);
        }
        SysRole entity = new SysRole();
        entity.setRoleCode(req.getRoleCode());
        entity.setRoleName(req.getRoleName());
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        entity.setRemark(blankToNull(req.getRemark()));
        roleMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, RoleUpdateRequest req) {
        SysRole current = roleMapper.selectById(id);
        if (current == null) {
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        if (notBlank(req.getRoleName())
                && !req.getRoleName().equals(current.getRoleName())
                && existsByName(req.getRoleName(), id)) {
            throw new BizException(ManagerResultCode.ROLE_NAME_EXISTS);
        }
        SysRole patch = new SysRole();
        patch.setId(id);
        if (req.getRoleName() != null) patch.setRoleName(req.getRoleName());
        if (req.getStatus() != null) patch.setStatus(req.getStatus());
        if (req.getRemark() != null) patch.setRemark(blankToNull(req.getRemark()));
        roleMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole current = roleMapper.selectById(id);
        if (current == null) {
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        Long boundUsers = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (boundUsers != null && boundUsers > 0) {
            throw new BizException(ManagerResultCode.ROLE_HAS_USERS);
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, id));
        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setDeleteTime(LocalDateTime.now());
        roleMapper.updateById(patch);
        roleMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND.getCode(), "状态值非法");
        }
        SysRole current = roleMapper.selectById(id);
        if (current == null) {
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setStatus(status);
        roleMapper.updateById(patch);
    }

    @Override
    public List<Long> listMenuIds(Long roleId) {
        ensureRoleExists(roleId);
        List<SysRoleMenu> rows = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        return rows.stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        ensureRoleExists(roleId);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        Set<Long> uniq = new HashSet<>(menuIds);
        for (Long menuId : uniq) {
            if (menuId == null) continue;
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    @Override
    public List<Long> listUserIds(Long roleId) {
        ensureRoleExists(roleId);
        List<SysUserRole> rows = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        return rows.stream().map(SysUserRole::getUserId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(Long roleId, List<Long> userIds) {
        ensureRoleExists(roleId);
        List<SysUserRole> existing = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        Set<Long> existingIds = existing.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
        Set<Long> targetIds = userIds == null ? Collections.emptySet() : new HashSet<>(userIds);
        targetIds.remove(null);

        Set<Long> toRemove = new HashSet<>(existingIds);
        toRemove.removeAll(targetIds);
        if (!toRemove.isEmpty()) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, roleId)
                    .in(SysUserRole::getUserId, toRemove));
        }

        Set<Long> toAdd = new HashSet<>(targetIds);
        toAdd.removeAll(existingIds);
        for (Long userId : toAdd) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private void ensureRoleExists(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ManagerResultCode.ROLE_NOT_FOUND);
        }
    }

    private void validateCode(String code) {
        if (!notBlank(code)) {
            throw new BizException(ManagerResultCode.ROLE_CODE_INVALID, "请输入角色编码");
        }
        if (!code.matches(ROLE_CODE_REGEX)) {
            throw new BizException(ManagerResultCode.ROLE_CODE_INVALID,
                    "角色编码仅允许字母 / 数字 / 下划线 / 短横线，长度 2-50");
        }
    }

    private boolean existsByCode(String code, Long excludeId) {
        if (!notBlank(code)) return false;
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code);
        if (excludeId != null) wrapper.ne(SysRole::getId, excludeId);
        Long count = roleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private boolean existsByName(String name, Long excludeId) {
        if (!notBlank(name)) return false;
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleName, name);
        if (excludeId != null) wrapper.ne(SysRole::getId, excludeId);
        Long count = roleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private RoleListVO toListVO(SysRole role) {
        RoleListVO vo = new RoleListVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}
