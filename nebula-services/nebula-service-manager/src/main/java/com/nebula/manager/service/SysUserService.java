package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.UserCreateRequest;
import com.nebula.manager.dto.UserPageQuery;
import com.nebula.manager.dto.UserUpdateRequest;
import com.nebula.manager.vo.UserDetailVO;
import com.nebula.manager.vo.UserListVO;

/**
 * 系统用户服务接口
 * 定义用户的分页查询、详情查看、创建、更新、删除以及状态和密码管理等业务方法
 *
 * @author nebula
 */
public interface SysUserService {

    /**
     * 分页查询用户列表
     * 根据查询条件返回用户分页数据
     *
     * @param query 用户分页查询条件
     * @return 分页结果，包含用户列表和分页信息
     */
    PageResult<UserListVO> page(UserPageQuery query);

    /**
     * 获取用户详情
     * 根据用户ID查询指定用户的详细信息
     *
     * @param id 用户ID
     * @return 用户详细信息
     */
    UserDetailVO detail(Long id);

    /**
     * 创建新用户
     * 根据用户创建请求数据保存新的用户记录
     *
     * @param request 用户创建请求对象，包含用户基本信息
     * @return 创建成功的用户ID
     */
    Long create(UserCreateRequest request);

    /**
     * 更新用户信息
     * 根据用户ID修改指定用户的数据
     *
     * @param id      用户ID
     * @param request 用户更新请求对象，包含要修改的字段
     */
    void update(Long id, UserUpdateRequest request);

    /**
     * 删除用户
     * 根据用户ID删除对应的用户记录
     *
     * @param id 用户ID
     */
    void delete(Long id);

    /**
     * 更新用户状态
     * 修改用户的启用/禁用状态
     *
     * @param id     用户ID
     * @param status 新的状态值
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置用户密码
     * 为指定用户设置新的密码
     *
     * @param id           用户ID
     * @param newPassword  新密码
     */
    void resetPassword(Long id, String newPassword);
}
