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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现类
 * 实现用户注册和登录相关的业务逻辑
 *
 * @author nebula
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 用户数据访问层
     */
    private final SysUserMapper userMapper;
    /**
     * 系统配置服务
     */
    private final SysConfigService configService;
    /**
     * 验证码服务
     */
    private final CaptchaService captchaService;
    /**
     * 密码编码器
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造函数注入依赖
     *
     * @param userMapper       用户数据访问层
     * @param configService    系统配置服务
     * @param captchaService   验证码服务
     * @param passwordEncoder  密码编码器
     */
    public AuthServiceImpl(SysUserMapper userMapper,
                           SysConfigService configService,
                           CaptchaService captchaService,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.configService = configService;
        this.captchaService = captchaService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册实现方法
     * 根据注册请求数据创建新用户账户
     *
     * @param req 注册请求对象，包含用户名、密码等注册信息
     * @return 创建成功的用户实体对象
     */
    @Override
    public SysUser register(RegisterRequest req) {
        log.info("用户注册: {}", req.getUsername());

        // 检查注册功能是否开启
        if (!configService.getBool(ManagerConfigKeys.REGISTER_ENABLED, ManagerConfigKeys.DEFAULT_REGISTER_ENABLED)) {
            log.warn("注册功能未开启，用户 {} 注册失败", req.getUsername());
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), "注册功能未开启");
        }

        // 验证验证码
        captchaService.assertVerified(req.getCaptchaType(), req.getCaptchaVerifyToken());

        // 验证用户名和密码格式
        validateUsername(req.getUsername());
        validatePassword(req.getPassword());

        // 检查用户名是否已存在
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", req.getUsername())) > 0) {
            log.warn("用户名 {} 已存在", req.getUsername());
            throw new BizException(ManagerResultCode.USERNAME_EXISTS);
        }

        // 检查邮箱是否已存在
        if (notBlank(req.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", req.getEmail())) > 0) {
            log.warn("邮箱 {} 已存在", req.getEmail());
            throw new BizException(ManagerResultCode.EMAIL_EXISTS);
        }

        // 检查手机号是否已存在
        if (notBlank(req.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("mobile", req.getMobile())) > 0) {
            log.warn("手机号 {} 已存在", req.getMobile());
            throw new BizException(ManagerResultCode.MOBILE_EXISTS);
        }

        // 创建新用户实体
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword())); // 加密密码
        user.setNickname(notBlank(req.getNickname()) ? req.getNickname() : req.getUsername()); // 设置昵称，如果未提供则使用用户名
        user.setEmail(notBlank(req.getEmail()) ? req.getEmail() : null); // 设置邮箱
        user.setMobile(notBlank(req.getMobile()) ? req.getMobile() : null); // 设置手机号
        user.setStatus(1); // 设置用户状态为启用

        // 保存用户到数据库
        userMapper.insert(user);
        log.info("用户注册成功，ID: {}", user.getId());

        // 清空密码字段，防止敏感信息泄露
        user.setPassword(null);
        return user;
    }

    /**
     * 用户登录实现方法
     * 验证用户登录凭据并返回用户信息
     *
     * @param req 登录请求对象，包含用户名和密码
     * @return 登录成功的用户实体对象
     */
    @Override
    public SysUser login(LoginRequest req) {
        log.info("用户登录: {}", req.getUsername());

        // 验证验证码
        captchaService.assertVerified(req.getCaptchaType(), req.getCaptchaVerifyToken());

        // 检查用户名和密码是否为空
        if (!notBlank(req.getUsername()) || !notBlank(req.getPassword())) {
            log.warn("用户名或密码为空，登录失败: {}", req.getUsername());
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 根据用户名查询用户信息
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", req.getUsername()));
        if (user == null) {
            log.warn("用户不存在: {}", req.getUsername());
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 验证密码是否匹配
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("密码错误: {}", req.getUsername());
            throw new BizException(ManagerResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 检查用户状态是否启用
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户已被禁用: {}", req.getUsername());
            throw new BizException(ManagerResultCode.USER_DISABLED);
        }

        // 在Sa-Token中登录用户
        StpUtil.login(user.getId());
        StpUtil.getRoleList();
        StpUtil.getPermissionList();
        log.info("用户登录成功，ID: {}", user.getId());

        // 清空密码字段，防止敏感信息泄露
        user.setPassword(null);
        return user;
    }

    /**
     * 验证用户名格式
     * 检查用户名长度和字符合法性
     *
     * @param username 要验证的用户名
     */
    private void validateUsername(String username) {
        if (username == null) {
            throw new BizException(ManagerResultCode.USERNAME_INVALID);
        }

        // 从配置中获取用户名长度限制
        int min = configService.getInt(ManagerConfigKeys.USERNAME_MIN_LENGTH, ManagerConfigKeys.DEFAULT_USERNAME_MIN_LENGTH);
        int max = configService.getInt(ManagerConfigKeys.USERNAME_MAX_LENGTH, ManagerConfigKeys.DEFAULT_USERNAME_MAX_LENGTH);

        // 检查用户名长度
        if (username.length() < min || username.length() > max) {
            throw new BizException(ManagerResultCode.USERNAME_INVALID,
                    String.format("用户名长度需 %d-%d 位", min, max));
        }

        // 检查用户名格式（字母开头，可包含字母、数字、下划线）
        if (!username.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new BizException(ManagerResultCode.USERNAME_INVALID, "用户名只能由字母、数字、下划线组成且以字母开头");
        }
    }

    /**
     * 验证密码格式
     * 检查密码长度是否符合要求
     *
     * @param password 要验证的密码
     */
    private void validatePassword(String password) {
        if (password == null) {
            throw new BizException(ManagerResultCode.PASSWORD_INVALID);
        }

        // 从配置中获取密码长度限制
        int min = configService.getInt(ManagerConfigKeys.PASSWORD_MIN_LENGTH, ManagerConfigKeys.DEFAULT_PASSWORD_MIN_LENGTH);
        int max = configService.getInt(ManagerConfigKeys.PASSWORD_MAX_LENGTH, ManagerConfigKeys.DEFAULT_PASSWORD_MAX_LENGTH);

        // 检查密码长度
        if (password.length() < min || password.length() > max) {
            throw new BizException(ManagerResultCode.PASSWORD_INVALID,
                    String.format("密码长度需 %d-%d 位", min, max));
        }
    }

    /**
     * 检查字符串是否非空且非空白
     *
     * @param s 要检查的字符串
     * @return 如果字符串非空且非空白则返回true，否则返回false
     */
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
