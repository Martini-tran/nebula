package com.nebula.manager.service.impl;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.crypto.AesUtil;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.security.apikey.ApiKeyMasker;
import com.nebula.manager.dto.ApiKeyPageQuery;
import com.nebula.manager.dto.ApiKeySaveRequest;
import com.nebula.manager.mapper.SysApiKeyMapper;
import com.nebula.manager.mapper.SysUserMapper;
import com.nebula.manager.service.SysApiKeyService;
import com.nebula.manager.vo.ApiKeyCreatedVO;
import com.nebula.manager.vo.ApiKeyVO;
import com.nebula.system.entity.SysApiKey;
import com.nebula.system.entity.SysUser;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * API Key（SK）管理服务实现
 *
 * <p>key 的生成、存储格式、过期与校验全部交给 Sa-Token 的 API Key 模块；本类只做两件它不管的事：
 * 把 key 落库（Sa-Token 只写 Redis），以及在每次增删改后把变更同步给它。运行时网关和各服务校验 key
 * 读的都是 Redis 里那份，所以库与 Redis 必须同步写——同步失败即抛出、回滚本次库操作。</p>
 *
 * <p>授权范围（scope）取值就是权限码，与接口上 {@code @SaCheckApiKey} 的 scope 对应。
 * 创建/修改时要求操作人自己拥有这些权限：否则有了「管理 key」的权限，就能给自己签一把什么都能干的 key。</p>
 *
 * @author nebula
 */
@Slf4j
@Service
public class SysApiKeyServiceImpl implements SysApiKeyService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private static final int NAME_MAX_LENGTH = 100;
    private static final int REMARK_MAX_LENGTH = 500;
    private static final int SCOPE_MAX_COUNT = 20;

    /** scope 落库时用逗号拼接，故单个 scope 里不允许出现逗号和空白 */
    private static final Pattern SCOPE_PATTERN = Pattern.compile("^[A-Za-z0-9:_-]{1,100}$");
    private static final String SCOPE_SEPARATOR = ",";

    private final SysApiKeyMapper apiKeyMapper;
    private final SysUserMapper userMapper;

    /**
     * key 密文的加密密钥。未单独配置时沿用模型档案的密钥，省一个部署变量；上线产生数据后不可更换。
     */
    @Value("${nebula.security.api-key.secret}")
    private String secret;

    public SysApiKeyServiceImpl(SysApiKeyMapper apiKeyMapper, SysUserMapper userMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<ApiKeyVO> page(ApiKeyPageQuery query) {
        ApiKeyPageQuery safe = query == null ? new ApiKeyPageQuery() : query;
        Page<SysApiKey> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<SysApiKey> wrapper = new LambdaQueryWrapper<SysApiKey>()
                .like(StringUtils.hasText(safe.getName()), SysApiKey::getName, safe.getName())
                .eq(safe.getStatus() != null, SysApiKey::getStatus, safe.getStatus())
                .orderByDesc(SysApiKey::getCreateTime);
        Page<SysApiKey> result = apiKeyMapper.selectPage(page, wrapper);

        Map<Long, String> usernames = loadUsernames(result.getRecords());
        List<ApiKeyVO> rows = result.getRecords().stream()
                .map(entity -> toVO(entity, usernames.get(entity.getUserId())))
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreatedVO create(ApiKeySaveRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未登录，无法创建 API Key");
        }
        validate(request);
        List<String> scopes = normalizeScopes(request.getScopes());
        checkGrantable(scopes);

        // key 由框架生成（带前缀的随机串，并保证不与现存 key 重复）
        String plainKey = SaApiKeyUtil.createApiKeyModel(userId).getApiKey();

        SysApiKey entity = new SysApiKey();
        entity.setUserId(userId);
        entity.setName(request.getName().trim());
        entity.setApiKey(AesUtil.encrypt(plainKey, secret));
        entity.setKeyMask(ApiKeyMasker.mask(plainKey));
        entity.setScopes(String.join(SCOPE_SEPARATOR, scopes));
        entity.setExpireTime(request.getExpireTime());
        entity.setStatus(STATUS_ENABLED);
        entity.setRemark(request.getRemark());
        apiKeyMapper.insert(entity);

        SaApiKeyUtil.saveApiKey(toModel(entity, plainKey));
        log.info("API Key 已创建, id={}, userId={}, scopes={}", entity.getId(), userId, scopes);

        ApiKeyCreatedVO vo = new ApiKeyCreatedVO();
        vo.setId(entity.getId());
        vo.setApiKey(plainKey);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ApiKeySaveRequest request) {
        validate(request);
        SysApiKey entity = getExisting(id);
        List<String> scopes = normalizeScopes(request.getScopes());
        checkGrantable(scopes);

        entity.setName(request.getName().trim());
        entity.setScopes(String.join(SCOPE_SEPARATOR, scopes));
        entity.setRemark(request.getRemark());
        entity.setExpireTime(request.getExpireTime());

        // 过期时间、备注允许改回空值，而 updateById 会跳过 null 字段，所以这两列用 set 显式写
        SysApiKey patch = new SysApiKey();
        patch.setName(entity.getName());
        patch.setScopes(entity.getScopes());
        apiKeyMapper.update(patch, new LambdaUpdateWrapper<SysApiKey>()
                .eq(SysApiKey::getId, id)
                .set(SysApiKey::getExpireTime, entity.getExpireTime())
                .set(SysApiKey::getRemark, entity.getRemark()));

        syncOne(entity);
        log.info("API Key 已更新, id={}, scopes={}", id, scopes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_DISABLED && status != STATUS_ENABLED)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值非法");
        }
        SysApiKey entity = getExisting(id);
        entity.setStatus(status);
        apiKeyMapper.updateById(entity);

        // 禁用的 key 仍留在缓存里（带禁用标记），调用方才能拿到「已被禁用」而不是笼统的「无效」
        syncOne(entity);
        log.info("API Key 状态已变更, id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysApiKey entity = getExisting(id);
        apiKeyMapper.deleteById(id);
        SaApiKeyUtil.deleteApiKey(AesUtil.decrypt(entity.getApiKey(), secret));
        log.info("API Key 已删除, id={}", id);
    }

    @Override
    public int syncToCache() {
        List<SysApiKey> keys = apiKeyMapper.selectList(new LambdaQueryWrapper<>());
        int synced = 0;
        for (SysApiKey entity : keys) {
            try {
                syncOne(entity);
                synced++;
            } catch (Exception e) {
                // 单条失败（多半是换过加密密钥导致解不开）不影响其余 key
                log.warn("API Key 同步到缓存失败, id={}, msg={}", entity.getId(), e.getMessage());
            }
        }
        return synced;
    }

    /**
     * 把一条 key 的当前状态写进 Sa-Token 缓存。已过期的 key 框架会直接从缓存移除。
     */
    private void syncOne(SysApiKey entity) {
        SaApiKeyUtil.saveApiKey(toModel(entity, AesUtil.decrypt(entity.getApiKey(), secret)));
    }

    private ApiKeyModel toModel(SysApiKey entity, String plainKey) {
        ApiKeyModel model = new ApiKeyModel()
                .setApiKey(plainKey)
                .setLoginId(entity.getUserId())
                .setTitle(entity.getName())
                .setIntro(entity.getRemark())
                .setScopes(new ArrayList<>(splitScopes(entity.getScopes())))
                .setIsValid(Integer.valueOf(STATUS_ENABLED).equals(entity.getStatus()))
                .setExpiresTime(entity.getExpireTime() == null
                        ? SaTokenDao.NEVER_EXPIRE
                        : toEpochMilli(entity.getExpireTime()));
        if (entity.getCreateTime() != null) {
            model.setCreateTime(toEpochMilli(entity.getCreateTime()));
        }
        return model;
    }

    private ApiKeyVO toVO(SysApiKey entity, String username) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setKeyMask(entity.getKeyMask());
        vo.setUserId(entity.getUserId());
        vo.setUsername(username);
        vo.setScopes(splitScopes(entity.getScopes()));
        vo.setExpireTime(entity.getExpireTime());
        vo.setExpired(entity.getExpireTime() != null && entity.getExpireTime().isBefore(LocalDateTime.now()));
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private Map<Long, String> loadUsernames(List<SysApiKey> keys) {
        Set<Long> userIds = keys.stream().map(SysApiKey::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));
    }

    private SysApiKey getExisting(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "API Key ID 不能为空");
        }
        SysApiKey entity = apiKeyMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "API Key 不存在: " + id);
        }
        return entity;
    }

    private void validate(ApiKeySaveRequest request) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (request.getName().trim().length() > NAME_MAX_LENGTH) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称长度不能超过 " + NAME_MAX_LENGTH);
        }
        if (request.getRemark() != null && request.getRemark().length() > REMARK_MAX_LENGTH) {
            throw new BizException(HttpStatus.BAD_REQUEST, "备注长度不能超过 " + REMARK_MAX_LENGTH);
        }
        if (request.getExpireTime() != null && !request.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "过期时间必须晚于当前时间");
        }
    }

    private List<String> normalizeScopes(List<String> scopes) {
        List<String> normalized = scopes == null ? List.of() : scopes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "至少选择一个授权范围");
        }
        if (normalized.size() > SCOPE_MAX_COUNT) {
            throw new BizException(HttpStatus.BAD_REQUEST, "授权范围不能超过 " + SCOPE_MAX_COUNT + " 个");
        }
        for (String scope : normalized) {
            if (!SCOPE_PATTERN.matcher(scope).matches()) {
                throw new BizException(HttpStatus.BAD_REQUEST, "授权范围格式非法: " + scope);
            }
        }
        return normalized;
    }

    /**
     * 只能授予操作人自己拥有的权限。
     */
    private void checkGrantable(List<String> scopes) {
        for (String scope : scopes) {
            if (!StpUtil.hasPermission(scope)) {
                throw new BizException(HttpStatus.FORBIDDEN, "不能授予自己没有的权限: " + scope);
            }
        }
    }

    private List<String> splitScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return List.of();
        }
        return Arrays.stream(scopes.split(SCOPE_SEPARATOR))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
