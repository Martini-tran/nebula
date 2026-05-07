package com.nebula.system.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.system.dto.UserCreateRequest;
import com.nebula.system.dto.UserPageQuery;
import com.nebula.system.dto.UserUpdateRequest;
import com.nebula.system.vo.UserDetailVO;
import com.nebula.system.vo.UserListVO;

/**
 * 系统用户管理业务接口
 *
 * @author nebula
 */
public interface SysUserService {

    /**
     * 分页查询用户
     */
    PageResult<UserListVO> page(UserPageQuery query);

    /**
     * 用户详情
     */
    UserDetailVO detail(Long id);

    /**
     * 创建用户：唯一性校验 → 格式校验 → BCrypt 加密 → 写库
     *
     * @return 新建用户主键
     */
    Long create(UserCreateRequest request);

    /**
     * 修改用户资料（不动 username / password）
     */
    void update(Long id, UserUpdateRequest request);

    /**
     * 软删除用户：MP @TableLogic 自动写 deleted=1
     */
    void delete(Long id);

    /**
     * 启用 / 禁用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 管理员重置密码
     */
    void resetPassword(Long id, String newPassword);
}
