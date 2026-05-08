package com.nebula.system.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.system.dto.RoleCreateRequest;
import com.nebula.system.dto.RolePageQuery;
import com.nebula.system.dto.RoleUpdateRequest;
import com.nebula.system.vo.RoleListVO;
import com.nebula.system.vo.RoleSimpleVO;
import java.util.List;

/**
 * 系统角色业务接口
 *
 * @author nebula
 */
public interface SysRoleService {

    /**
     * 分页查询角色
     */
    PageResult<RoleListVO> page(RolePageQuery query);

    /**
     * 列出全部启用的角色（下拉、勾选场景）
     */
    List<RoleSimpleVO> listEnabled();

    /**
     * 详情
     */
    RoleListVO detail(Long id);

    /**
     * 创建角色，返回主键
     */
    Long create(RoleCreateRequest request);

    /**
     * 更新角色
     */
    void update(Long id, RoleUpdateRequest request);

    /**
     * 删除角色（角色仍有绑定用户时拒绝）；同步清理 sys_role_menu / sys_user_role
     */
    void delete(Long id);

    /**
     * 切换状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 取角色当前授权的菜单 ID 集合
     */
    List<Long> listMenuIds(Long roleId);

    /**
     * 全量覆盖角色 - 菜单关联
     */
    void assignMenus(Long roleId, List<Long> menuIds);

    /**
     * 取角色当前绑定的用户 ID 集合
     */
    List<Long> listUserIds(Long roleId);

    /**
     * 全量覆盖角色 - 用户关联
     */
    void assignUsers(Long roleId, List<Long> userIds);
}
