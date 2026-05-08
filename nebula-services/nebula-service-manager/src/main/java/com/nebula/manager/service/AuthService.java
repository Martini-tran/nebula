package com.nebula.manager.service;

import com.nebula.manager.dto.LoginRequest;
import com.nebula.manager.dto.RegisterRequest;
import com.nebula.system.entity.SysUser;

public interface AuthService {

    SysUser register(RegisterRequest request);

    SysUser login(LoginRequest request);
}
