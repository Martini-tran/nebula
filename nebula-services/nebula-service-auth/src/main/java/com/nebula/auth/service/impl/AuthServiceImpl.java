package com.nebula.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nebula.auth.constant.AuthConfigKeys;
import com.nebula.auth.enums.AuthResultCode;
import com.nebula.auth.dto.LoginRequest;
import com.nebula.auth.dto.RegisterRequest;
import com.nebula.auth.mapper.SysUserMapper;
import com.nebula.auth.service.AuthService;
import com.nebula.common.captcha.service.CaptchaService;
import com.nebula.common.config.service.SysConfigService;
import com.nebula.common.core.exception.BizException;
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
        if (!configService.getBool(AuthConfigKeys.REGISTER_ENABLED, AuthConfigKeys.DEFAULT_REGISTER_ENABLED)) {
            throw new BizException(AuthResultCode.REGISTER_DISABLED);
        }
        captchaService.assertVerified(req.getCaptchaType(), req.getCaptchaVerifyToken());

        validateUsername(req.getUsername());
        validatePassword(req.getPassword());

        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", req.getUsername())) > 0) {
            throw new BizException(AuthResultCode.USERNAME_EXISTS);
        }
        if (notBlank(req.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", req.getEmail())) > 0) {
            throw new BizException(AuthResultCode.EMAIL_EXISTS);
        }
        if (notBlank(req.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("mobile", req.getMobile())) > 0) {
            throw new BizException(AuthResultCode.MOBILE_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(notBlank(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setEmail(notBlank(req.getEmail()) ? req.getEmail() : null);
        user.setMobile(notBlank(req.getMobile()) ? req.getMobile() : null);
        user.setStatus(1);
        userMapper.insert(user);
        // 不回写 password 到响应
        user.setPassword(null);
        return user;
    }

    @Override
    public SysUser login(LoginRequest req) {
        captchaService.assertVerified(req.getCaptchaType(), req.getCaptchaVerifyToken());

        if (!notBlank(req.getUsername()) || !notBlank(req.getPassword())) {
            throw new BizException(AuthResultCode.PASSWORD_MISMATCH);
        }

        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", req.getUsername()));
        if (user == null) {
            throw new BizException(AuthResultCode.PASSWORD_MISMATCH);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(AuthResultCode.PASSWORD_MISMATCH);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(AuthResultCode.USER_DISABLED);
        }

        StpUtil.login(user.getId());
        user.setPassword(null);
        return user;
    }

    // ===== helpers =====

    private void validateUsername(String username) {
        if (username == null) {
            throw new BizException(AuthResultCode.USERNAME_INVALID);
        }
        int min = configService.getInt(AuthConfigKeys.USERNAME_MIN_LENGTH, AuthConfigKeys.DEFAULT_USERNAME_MIN_LENGTH);
        int max = configService.getInt(AuthConfigKeys.USERNAME_MAX_LENGTH, AuthConfigKeys.DEFAULT_USERNAME_MAX_LENGTH);
        if (username.length() < min || username.length() > max) {
            throw new BizException(AuthResultCode.USERNAME_INVALID,
                    String.format("用户名长度需 %d-%d 位", min, max));
        }
        if (!username.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new BizException(AuthResultCode.USERNAME_INVALID, "用户名只能由字母、数字、下划线组成且以字母开头");
        }
    }

    private void validatePassword(String password) {
        if (password == null) {
            throw new BizException(AuthResultCode.PASSWORD_INVALID);
        }
        int min = configService.getInt(AuthConfigKeys.PASSWORD_MIN_LENGTH, AuthConfigKeys.DEFAULT_PASSWORD_MIN_LENGTH);
        int max = configService.getInt(AuthConfigKeys.PASSWORD_MAX_LENGTH, AuthConfigKeys.DEFAULT_PASSWORD_MAX_LENGTH);
        if (password.length() < min || password.length() > max) {
            throw new BizException(AuthResultCode.PASSWORD_INVALID,
                    String.format("密码长度需 %d-%d 位", min, max));
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
