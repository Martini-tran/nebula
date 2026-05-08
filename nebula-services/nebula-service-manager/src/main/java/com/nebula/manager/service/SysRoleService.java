package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.RoleCreateRequest;
import com.nebula.manager.dto.RolePageQuery;
import com.nebula.manager.dto.RoleUpdateRequest;
import com.nebula.manager.vo.RoleListVO;
import com.nebula.manager.vo.RoleSimpleVO;
import java.util.List;

/**
 * 系统角色服务接口
 * 定义角色的分页查询、详情查看、创建、更新、删除以及权限分配等业务方法
 *
 * @author nebula
 */
public interface SysRoleService {

    /**
     * 分页查询角色列表
     * 根据查询条件返回角色分页数据
     *
     * @param query 角色分页查询条件
     * @return 分页结果，包含角色列表和分页信息
     */
    PageResult<RoleListVO> page(RolePageQuery query);

    /**
     * 获取启用的角色列表
     * 返回所有启用状态的角色简要信息
     *
     * @return 启用角色列表
     */
    List<RoleSimpleVO> listEnabled();

    /**
     * 获取角色详情
     * 根据角色ID查询指定角色的详细信息
     *
     * @param id 角色ID
     * @return 角色详细信息
     */
    RoleListVO detail(Long id);

    /**
     * 创建新角色
     * 根据角色创建请求数据保存新的角色记录
     *
     * @param request 角色创建请求对象，包含角色基本信息
     * @return 创建成功的角色ID
     */
    Long create(RoleCreateRequest request);

    /**
     * 更新角色信息
     * 根据角色ID修改指定角色的数据
     *
     * @param id      角色ID
     * @param request 角色更新请求对象，包含要修改的字段
     */
    void update(Long id, RoleUpdateRequest request);

    /**
     * 删除角色
     * 根据角色ID删除对应的角色记录
     *
     * @param id 角色ID
     */
    void delete(Long id);

    /**
     * 更新角色状态
     * 修改角色的启用/禁用状态
     *
     * @param id     角色ID
     * @param status 新的状态值
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取角色关联的菜单ID列表
     * 查询指定角色所拥有的菜单权限ID
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    List<Long> listMenuIds(Long roleId);

    /**
     * 为角色分配菜单权限
     * 将指定的菜单ID列表分配给角色
     *
     * @param roleId  角色ID
     * @param menuIds 要分配的菜单ID列表
     */
    void assignMenus(Long roleId, List<Long> menuIds);

    /**
     * 获取角色关联的用户ID列表
     * 查询指定角色所拥有的用户ID
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    List<Long> listUserIds(Long roleId);

    /**
     * 为角色分配用户
     * 将指定的用户ID列表分配给角色
     *
     * @param roleId   角色ID
     * @param userIds  要分配的用户ID列表
     */
    void assignUsers(Long roleId, List<Long> userIds);
}


