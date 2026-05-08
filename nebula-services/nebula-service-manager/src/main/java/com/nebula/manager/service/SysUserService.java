package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.UserCreateRequest;
import com.nebula.manager.dto.UserPageQuery;
import com.nebula.manager.dto.UserUpdateRequest;
import com.nebula.manager.vo.UserDetailVO;
import com.nebula.manager.vo.UserListVO;

public interface SysUserService {

    PageResult<UserListVO> page(UserPageQuery query);

    UserDetailVO detail(Long id);

    Long create(UserCreateRequest request);

    void update(Long id, UserUpdateRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String newPassword);
}
