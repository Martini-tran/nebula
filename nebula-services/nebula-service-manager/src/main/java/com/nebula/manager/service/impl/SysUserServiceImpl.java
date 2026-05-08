package com.nebula.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.UserCreateRequest;
import com.nebula.manager.dto.UserPageQuery;
import com.nebula.manager.dto.UserUpdateRequest;
import com.nebula.manager.enums.ManagerResultCode;
import com.nebula.manager.mapper.SysUserMapper;
import com.nebula.manager.service.SysUserService;
import com.nebula.manager.vo.UserDetailVO;
import com.nebula.manager.vo.UserListVO;
import com.nebula.system.entity.SysUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统用户服务实现类
 * 提供用户的增删改查、状态更新、密码重置等功能
 *
 * @author nebula
 */
@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    /**
     * 用户名正则表达式：以字母开头，后跟字母、数字、下划线
     */
    private static final String USERNAME_REGEX = "^[A-Za-z][A-Za-z0-9_]*$";

    /**
     * 用户名最小长度
     */
    private static final int USERNAME_MIN = 4;

    /**
     * 用户名最大长度
     */
    private static final int USERNAME_MAX = 32;

    /**
     * 密码最小长度
     */
    private static final int PASSWORD_MIN = 6;

    /**
     * 密码最大长度
     */
    private static final int PASSWORD_MAX = 64;

    /**
     * 日期时间格式化器数组
     */
    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    /**
     * 用户数据访问层
     */
    private final SysUserMapper userMapper;

    /**
     * 密码编码器
     */
    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户列表
     * 根据查询条件分页返回用户列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<UserListVO> page(UserPageQuery query) {
        log.info("开始分页查询用户列表，页码: {}, 页大小: {}", query.safePageNum(), query.safePageSize());

        Page<SysUser> page = new Page<>(query.safePageNum(), query.safePageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper
                .like(notBlank(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(notBlank(query.getNickname()), SysUser::getNickname, query.getNickname())
                .eq(notBlank(query.getMobile()), SysUser::getMobile, query.getMobile())
                .eq(notBlank(query.getEmail()), SysUser::getEmail, query.getEmail())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .ge(notBlank(query.getCreateTimeStart()), SysUser::getCreateTime,
                        parseDateTime(query.getCreateTimeStart()))
                .le(notBlank(query.getCreateTimeEnd()), SysUser::getCreateTime,
                        parseDateTime(query.getCreateTimeEnd()))
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        List<UserListVO> rows = result.getRecords().stream().map(this::toListVO).toList();

        log.info("用户列表分页查询完成，总记录数: {}, 返回记录数: {}", result.getTotal(), rows.size());
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取用户详情
     * 根据ID获取用户详细信息
     *
     * @param id 用户ID
     * @return 用户详情
     */
    @Override
    public UserDetailVO detail(Long id) {
        log.info("开始获取用户详情，ID: {}", id);

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            log.warn("用户不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.USER_NOT_FOUND);
        }

        UserDetailVO vo = new UserDetailVO();
        BeanUtils.copyProperties(user, vo);

        log.info("用户详情获取成功，ID: {}", id);
        return vo;
    }

    /**
     * 创建新用户
     * 根据请求参数创建新的用户记录
     *
     * @param req 创建请求参数
     * @return 新创建用户的ID
     */
    @Override
    public Long create(UserCreateRequest req) {
        log.info("开始创建用户，用户名: {}", req.getUsername());

        validateUsername(req.getUsername());
        validatePassword(req.getPassword());

        // 检查用户名是否已存在
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", req.getUsername())) > 0) {
            log.warn("用户名已存在: {}", req.getUsername());
            throw new BizException(ManagerResultCode.USERNAME_EXISTS);
        }

        // 检查手机号是否已存在
        if (notBlank(req.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("mobile", req.getMobile())) > 0) {
            log.warn("手机号已存在: {}", req.getMobile());
            throw new BizException(ManagerResultCode.MOBILE_EXISTS);
        }

        // 检查邮箱是否已存在
        if (notBlank(req.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", req.getEmail())) > 0) {
            log.warn("邮箱已存在: {}", req.getEmail());
            throw new BizException(ManagerResultCode.EMAIL_EXISTS);
        }

        // 构建用户实体
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(notBlank(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setAvatar(blankToNull(req.getAvatar()));
        user.setMobile(blankToNull(req.getMobile()));
        user.setEmail(blankToNull(req.getEmail()));
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        user.setRemark(blankToNull(req.getRemark()));

        int insertCount = userMapper.insert(user);
        log.info("用户创建成功，ID: {}，影响行数: {}", user.getId(), insertCount);
        return user.getId();
    }

    /**
     * 更新用户信息
     * 根据ID和请求参数更新用户记录
     *
     * @param id  用户ID
     * @param req 更新请求参数
     */
    @Override
    public void update(Long id, UserUpdateRequest req) {
        log.info("开始更新用户，ID: {}", id);

        SysUser current = userMapper.selectById(id);
        if (current == null) {
            log.warn("用户不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.USER_NOT_FOUND);
        }

        // 检查手机号是否重复
        if (notBlank(req.getMobile())
                && !req.getMobile().equals(current.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>()
                .eq("mobile", req.getMobile())
                .ne("id", id)) > 0) {
            log.warn("手机号已存在: {}", req.getMobile());
            throw new BizException(ManagerResultCode.MOBILE_EXISTS);
        }

        // 检查邮箱是否重复
        if (notBlank(req.getEmail())
                && !req.getEmail().equals(current.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>()
                .eq("email", req.getEmail())
                .ne("id", id)) > 0) {
            log.warn("邮箱已存在: {}", req.getEmail());
            throw new BizException(ManagerResultCode.EMAIL_EXISTS);
        }

        // 构建更新实体
        SysUser patch = new SysUser();
        patch.setId(id);
        if (req.getNickname() != null) patch.setNickname(req.getNickname());
        if (req.getAvatar() != null) patch.setAvatar(blankToNull(req.getAvatar()));
        if (req.getMobile() != null) patch.setMobile(blankToNull(req.getMobile()));
        if (req.getEmail() != null) patch.setEmail(blankToNull(req.getEmail()));
        if (req.getStatus() != null) patch.setStatus(req.getStatus());
        if (req.getRemark() != null) patch.setRemark(blankToNull(req.getRemark()));

        int updateCount = userMapper.updateById(patch);
        log.info("用户更新成功，ID: {}，影响行数: {}", id, updateCount);
    }

    /**
     * 删除用户
     * 根据ID软删除用户
     *
     * @param id 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("开始删除用户，ID: {}", id);

        SysUser current = userMapper.selectById(id);
        if (current == null) {
            log.warn("用户不存在，无法删除，ID: {}", id);
            throw new BizException(ManagerResultCode.USER_NOT_FOUND);
        }

        // 软删除用户
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setDeleteTime(LocalDateTime.now());
        int updateCount = userMapper.updateById(patch);

        // 物理删除用户
        int deleteCount = userMapper.deleteById(id);

        log.info("用户删除成功，ID: {}，软删除影响行数: {}，物理删除影响行数: {}",
                id, updateCount, deleteCount);
    }

    /**
     * 更新用户状态
     * 更新用户的启用/禁用状态
     *
     * @param id     用户ID
     * @param status 状态值（1启用，0禁用）
     */
    @Override
    public void updateStatus(Long id, Integer status) {
        log.info("开始更新用户状态，ID: {}，状态: {}", id, status);

        if (status == null || (status != 0 && status != 1)) {
            log.warn("用户状态值非法: {}", status);
            throw new BizException(ManagerResultCode.USER_NOT_FOUND.getCode(), "状态值非法");
        }

        SysUser current = userMapper.selectById(id);
        if (current == null) {
            log.warn("用户不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.USER_NOT_FOUND);
        }

        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setStatus(status);

        int updateCount = userMapper.updateById(patch);
        log.info("用户状态更新成功，ID: {}，影响行数: {}", id, updateCount);
    }

    /**
     * 重置用户密码
     * 为指定用户重置密码
     *
     * @param id          用户ID
     * @param newPassword 新密码
     */
    @Override
    public void resetPassword(Long id, String newPassword) {
        log.info("开始重置用户密码，ID: {}", id);

        validatePassword(newPassword);
        SysUser current = userMapper.selectById(id);
        if (current == null) {
            log.warn("用户不存在，ID: {}", id);
            throw new BizException(ManagerResultCode.USER_NOT_FOUND);
        }

        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(newPassword));

        int updateCount = userMapper.updateById(patch);
        log.info("用户密码重置成功，ID: {}，影响行数: {}", id, updateCount);
    }

    /**
     * 验证用户名格式
     * 用户名以字母开头，后跟字母、数字、下划线，长度在指定范围内
     *
     * @param username 用户名
     */
    private void validateUsername(String username) {
        if (username == null
                || username.length() < USERNAME_MIN
                || username.length() > USERNAME_MAX) {
            log.warn("用户名长度不符合要求: {}", username);
            throw new BizException(ManagerResultCode.USERNAME_INVALID,
                    String.format("用户名长度需 %d-%d 位", USERNAME_MIN, USERNAME_MAX));
        }
        if (!username.matches(USERNAME_REGEX)) {
            log.warn("用户名格式不符合要求: {}", username);
            throw new BizException(ManagerResultCode.USERNAME_INVALID,
                    "用户名只能由字母、数字、下划线组成且以字母开头");
        }
    }

    /**
     * 验证密码格式
     * 密码长度在指定范围内
     *
     * @param password 密码
     */
    private void validatePassword(String password) {
        if (password == null
                || password.length() < PASSWORD_MIN
                || password.length() > PASSWORD_MAX) {
            log.warn("密码长度不符合要求，长度: {}", password != null ? password.length() : 0);
            throw new BizException(ManagerResultCode.PASSWORD_INVALID,
                    String.format("密码长度需 %d-%d 位", PASSWORD_MIN, PASSWORD_MAX));
        }
    }

    /**
     * 将用户实体转换为列表视图对象
     *
     * @param user 用户实体
     * @return 列表视图对象
     */
    private UserListVO toListVO(SysUser user) {
        UserListVO vo = new UserListVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * 检查字符串是否非空
     *
     * @param s 字符串
     * @return 是否非空
     */
    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 空值转null
     *
     * @param s 原值
     * @return 转换后的值
     */
    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }

    /**
     * 解析日期时间字符串
     *
     * @param text 日期时间字符串
     * @return 解析后的本地日期时间
     */
    private static LocalDateTime parseDateTime(String text) {
        if (!notBlank(text)) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (Exception ignored) {
            }
        }
        log.warn("时间格式不合法: {}", text);
        throw new BizException(400, "时间格式不合法: " + text);
    }
}


