package com.nebula.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.system.dto.UserCreateRequest;
import com.nebula.system.dto.UserPageQuery;
import com.nebula.system.dto.UserUpdateRequest;
import com.nebula.system.entity.SysUser;
import com.nebula.system.enums.SystemResultCode;
import com.nebula.system.mapper.SysUserMapper;
import com.nebula.system.service.SysUserService;
import com.nebula.system.vo.UserDetailVO;
import com.nebula.system.vo.UserListVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysUserServiceImpl implements SysUserService {

    /**
     * 用户名格式：以字母开头，仅含字母 / 数字 / 下划线
     */
    private static final String USERNAME_REGEX = "^[A-Za-z][A-Za-z0-9_]*$";
    private static final int USERNAME_MIN = 4;
    private static final int USERNAME_MAX = 32;
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 64;

    /**
     * 与前端 UserPageQuery#createTimeStart/End 容许的两种格式
     */
    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResult<UserListVO> page(UserPageQuery query) {
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
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public UserDetailVO detail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(SystemResultCode.USER_NOT_FOUND);
        }
        UserDetailVO vo = new UserDetailVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public Long create(UserCreateRequest req) {
        validateUsername(req.getUsername());
        validatePassword(req.getPassword());

        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", req.getUsername())) > 0) {
            throw new BizException(SystemResultCode.USERNAME_EXISTS);
        }
        if (notBlank(req.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("mobile", req.getMobile())) > 0) {
            throw new BizException(SystemResultCode.MOBILE_EXISTS);
        }
        if (notBlank(req.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", req.getEmail())) > 0) {
            throw new BizException(SystemResultCode.EMAIL_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(notBlank(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setAvatar(blankToNull(req.getAvatar()));
        user.setMobile(blankToNull(req.getMobile()));
        user.setEmail(blankToNull(req.getEmail()));
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        user.setRemark(blankToNull(req.getRemark()));
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public void update(Long id, UserUpdateRequest req) {
        SysUser current = userMapper.selectById(id);
        if (current == null) {
            throw new BizException(SystemResultCode.USER_NOT_FOUND);
        }

        if (notBlank(req.getMobile())
                && !req.getMobile().equals(current.getMobile())
                && userMapper.selectCount(new QueryWrapper<SysUser>()
                        .eq("mobile", req.getMobile())
                        .ne("id", id)) > 0) {
            throw new BizException(SystemResultCode.MOBILE_EXISTS);
        }
        if (notBlank(req.getEmail())
                && !req.getEmail().equals(current.getEmail())
                && userMapper.selectCount(new QueryWrapper<SysUser>()
                        .eq("email", req.getEmail())
                        .ne("id", id)) > 0) {
            throw new BizException(SystemResultCode.EMAIL_EXISTS);
        }

        // 用 partial 实体只更新指定字段，避免把 username / password 覆盖
        SysUser patch = new SysUser();
        patch.setId(id);
        if (req.getNickname() != null) patch.setNickname(req.getNickname());
        if (req.getAvatar() != null) patch.setAvatar(blankToNull(req.getAvatar()));
        if (req.getMobile() != null) patch.setMobile(blankToNull(req.getMobile()));
        if (req.getEmail() != null) patch.setEmail(blankToNull(req.getEmail()));
        if (req.getStatus() != null) patch.setStatus(req.getStatus());
        if (req.getRemark() != null) patch.setRemark(blankToNull(req.getRemark()));
        userMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser current = userMapper.selectById(id);
        if (current == null) {
            throw new BizException(SystemResultCode.USER_NOT_FOUND);
        }
        // SysUser.deleteTime 没有 @TableField(fill=...)，先显式打时间戳
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setDeleteTime(LocalDateTime.now());
        userMapper.updateById(patch);
        // MP @TableLogic 把 delete 改写成 UPDATE deleted=1，update_time / update_by 由 AuditMetaObjectHandler 自动填
        userMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(SystemResultCode.USER_NOT_FOUND.getCode(), "状态值非法");
        }
        SysUser current = userMapper.selectById(id);
        if (current == null) {
            throw new BizException(SystemResultCode.USER_NOT_FOUND);
        }
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setStatus(status);
        userMapper.updateById(patch);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        validatePassword(newPassword);
        SysUser current = userMapper.selectById(id);
        if (current == null) {
            throw new BizException(SystemResultCode.USER_NOT_FOUND);
        }
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(patch);
    }

    // ===== helpers =====

    private void validateUsername(String username) {
        if (username == null
                || username.length() < USERNAME_MIN
                || username.length() > USERNAME_MAX) {
            throw new BizException(SystemResultCode.USERNAME_INVALID,
                    String.format("用户名长度需 %d-%d 位", USERNAME_MIN, USERNAME_MAX));
        }
        if (!username.matches(USERNAME_REGEX)) {
            throw new BizException(SystemResultCode.USERNAME_INVALID,
                    "用户名只能由字母、数字、下划线组成且以字母开头");
        }
    }

    private void validatePassword(String password) {
        if (password == null
                || password.length() < PASSWORD_MIN
                || password.length() > PASSWORD_MAX) {
            throw new BizException(SystemResultCode.PASSWORD_INVALID,
                    String.format("密码长度需 %d-%d 位", PASSWORD_MIN, PASSWORD_MAX));
        }
    }

    private UserListVO toListVO(SysUser user) {
        UserListVO vo = new UserListVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }

    private static LocalDateTime parseDateTime(String text) {
        if (!notBlank(text)) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        throw new BizException(400, "时间格式不合法: " + text);
    }
}
