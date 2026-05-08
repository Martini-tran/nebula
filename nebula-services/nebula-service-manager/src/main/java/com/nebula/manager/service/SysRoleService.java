package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.RoleCreateRequest;
import com.nebula.manager.dto.RolePageQuery;
import com.nebula.manager.dto.RoleUpdateRequest;
import com.nebula.manager.vo.RoleListVO;
import com.nebula.manager.vo.RoleSimpleVO;
import java.util.List;

public interface SysRoleService {

    PageResult<RoleListVO> page(RolePageQuery query);

    List<RoleSimpleVO> listEnabled();

    RoleListVO detail(Long id);

    Long create(RoleCreateRequest request);

    void update(Long id, RoleUpdateRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    List<Long> listMenuIds(Long roleId);

    void assignMenus(Long roleId, List<Long> menuIds);

    List<Long> listUserIds(Long roleId);

    void assignUsers(Long roleId, List<Long> userIds);
}
