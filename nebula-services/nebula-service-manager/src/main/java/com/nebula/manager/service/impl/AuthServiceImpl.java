package com.nebula.manager.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nebula.common.captcha.service.CaptchaService;
import com.nebula.common.config.service.SysConfigService;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.constant.ManagerConfigKeys;
import com.nebula.manager.dto.LoginRequest;
import com.nebula.manager.dto.RegisterRequest;
import com.nebula.manager.enums.ManagerResultCode;
import com.nebula.manager.mapper.SysUserMapper;
import com.nebula.manager.service.AuthService;
import com.nebula.system.entity.SysUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysConfigService configService;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper userMapper,
                           SysConfigService configService,
                           CaptchaService captchaService,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.configService = configService;
        this.captchaService = captchaService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SysUser register(RegisterRequest req) {
        if (!configService.getBool(ManagerConfigKeys.REGISTER_ENABLED, ManagerConfigKeys.DEFAULT_REGISTER_ENABLED)) {
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), "注册功能未开启");
        }
        captchaService.assertVerified(req.getCaptchaType(), req.getCaptchaVerifyToken());

        validateUsername(req.getUsername());
        validatePassword(req.getPassword());

        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", req.getUsername())) > 0) {
            throw new BizException(ManagerResultCode.USERNAME_EXISTS);
        }
        if (notBlank(req.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", req.getEmail())) > 0) {
            throw new BizException(ManagerResultCode.EMAIL_EXISTS);
        }
        if (notBlank(req.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("mobile", req.getMobile())) > 0) {
            throw new BizException(ManagerResultCode.MOBILE_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(notBlank(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setEmail(notBlank(req.getEmail()) ? req.getEmail() : null);
        user.setMobile(notBlank(req.getMobile()) ? req.getMobile() : null);
        user.setStatus(1);
        userMapper.insert(user);
        user.setPassword(null);
        return user;
    }

    @Override
    public SysUser login(LoginRequest req) {
        captchaService.assertVerified(req.getCaptchaType(), req.getCaptchaVerifyToken());

        if (!notBlank(req.getUsername()) || !notBlank(req.getPassword())) {
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", req.getUsername()));
        if (user == null) {
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ManagerResultCode.USER_DISABLED);
        }

        StpUtil.login(user.getId());
        user.setPassword(null);
        return user;
    }

    private void validateUsername(String username) {
        if (username == null) {
            throw new BizException(ManagerResultCode.USERNAME_INVALID);
        }
        int min = configService.getInt(ManagerConfigKeys.USERNAME_MIN_LENGTH, ManagerConfigKeys.DEFAULT_USERNAME_MIN_LENGTH);
        int max = configService.getInt(ManagerConfigKeys.USERNAME_MAX_LENGTH, ManagerConfigKeys.DEFAULT_USERNAME_MAX_LENGTH);
        if (username.length() < min || username.length() > max) {
            throw new BizException(ManagerResultCode.USERNAME_INVALID,
                    String.format("用户名长度需 %d-%d 位", min, max));
        }
        if (!username.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new BizException(ManagerResultCode.USERNAME_INVALID, "用户名只能由字母、数字、下划线组成且以字母开头");
        }
    }

    private void validatePassword(String password) {
        if (password == null) {
            throw new BizException(ManagerResultCode.PASSWORD_INVALID);
        }
        int min = configService.getInt(ManagerConfigKeys.PASSWORD_MIN_LENGTH, ManagerConfigKeys.DEFAULT_PASSWORD_MIN_LENGTH);
        int max = configService.getInt(ManagerConfigKeys.PASSWORD_MAX_LENGTH, ManagerConfigKeys.DEFAULT_PASSWORD_MAX_LENGTH);
        if (password.length() < min || password.length() > max) {
            throw new BizException(ManagerResultCode.PASSWORD_INVALID,
                    String.format("密码长度需 %d-%d 位", min, max));
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
