package com.nebula.manager.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.store.AiModelProfile;
import com.nebula.common.ai.flow.store.AiModelProfileMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.crypto.AesUtil;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.ModelProfilePageQuery;
import com.nebula.manager.dto.ModelProfileSaveRequest;
import com.nebula.manager.vo.ModelProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * AI模型档案管理服务实现（管理员端）
 * 直接操作 {@code ai_model_profile} 表完成 CRUD：写入端对明文 apiKey 加密、对 options 序列化为 JSON 字符串；
 * 读出端对 apiKey 仅以掩码暴露，绝不回传明文。加解密密钥与 {@code DatabaseModelProfileRepository} 共用
 * 配置 {@code nebula.ai.profile.secret}，确保管理端写入的密钥可被运行时仓储正确解密。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ModelProfileAdminServiceImpl implements ModelProfileAdminService {

    private final AiModelProfileMapper profileMapper;

    private final ObjectMapper objectMapper;

    @Value("${nebula.ai.profile.secret:nebula-ai-profile-default-secret}")
    private String secret;

    @Override
    public PageResult<ModelProfileVO> page(ModelProfilePageQuery query) {
        ModelProfilePageQuery safe = query == null ? new ModelProfilePageQuery() : query;
        Page<AiModelProfile> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiModelProfile> wrapper = new LambdaQueryWrapper<AiModelProfile>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiModelProfile::getProfileCode, safe.getKeyword())
                        .or()
                        .like(AiModelProfile::getName, safe.getKeyword())
                        .or()
                        .like(AiModelProfile::getModel, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getProvider()), AiModelProfile::getProvider, safe.getProvider())
                .eq(safe.getStatus() != null, AiModelProfile::getStatus, safe.getStatus())
                .orderByDesc(AiModelProfile::getUpdateTime);
        Page<AiModelProfile> result = profileMapper.selectPage(page, wrapper);
        List<ModelProfileVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ModelProfileVO detail(Long id) {
        AiModelProfile entity = getExisting(id);
        return toVO(entity);
    }

    @Override
    public Long create(ModelProfileSaveRequest request) {
        validate(request, true);
        if (exists(request.getProfileCode(), null)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "档案编码已存在: " + request.getProfileCode());
        }
        AiModelProfile entity = new AiModelProfile();
        applyRequest(entity, request, true);
        profileMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, ModelProfileSaveRequest request) {
        validate(request, false);
        AiModelProfile entity = getExisting(id);
        // 档案编码不可变更，忽略请求中的 profileCode
        applyRequest(entity, request, false);
        profileMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        getExisting(id);
        profileMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值非法");
        }
        AiModelProfile entity = getExisting(id);
        entity.setStatus(status);
        profileMapper.updateById(entity);
    }

    /**
     * 将保存请求合并进实体：加密 apiKey、序列化 options。
     *
     * @param entity   目标实体
     * @param request  保存请求
     * @param creating 是否为创建（创建时写入 profileCode 与默认状态）
     */
    private void applyRequest(AiModelProfile entity, ModelProfileSaveRequest request, boolean creating) {
        if (creating) {
            entity.setProfileCode(request.getProfileCode());
            entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        } else if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setName(request.getName());
        entity.setProvider(request.getProvider());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setModel(request.getModel());
        entity.setTemperature(request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setTopP(request.getTopP());
        entity.setTimeoutMs(request.getTimeoutMs());
        entity.setRemark(request.getRemark());
        entity.setOptions(writeOptions(request.getOptions()));
        // apiKey：有明文则加密覆盖；留空时创建写 null、更新保留原值
        if (StringUtils.hasText(request.getApiKey())) {
            entity.setApiKey(AesUtil.encrypt(request.getApiKey(), secret));
        } else if (creating) {
            entity.setApiKey(null);
        }
    }

    /**
     * 实体转 VO：apiKey 掩码处理，不回传明文。
     */
    private ModelProfileVO toVO(AiModelProfile entity) {
        ModelProfileVO vo = new ModelProfileVO();
        vo.setId(entity.getId());
        vo.setProfileCode(entity.getProfileCode());
        vo.setName(entity.getName());
        vo.setProvider(entity.getProvider());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setModel(entity.getModel());
        vo.setTemperature(entity.getTemperature());
        vo.setMaxTokens(entity.getMaxTokens());
        vo.setTopP(entity.getTopP());
        vo.setTimeoutMs(entity.getTimeoutMs());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setOptions(readOptions(entity.getOptions()));
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        boolean hasKey = StringUtils.hasText(entity.getApiKey());
        vo.setHasApiKey(hasKey);
        vo.setApiKeyMasked(hasKey ? mask(entity.getApiKey()) : null);
        return vo;
    }

    /**
     * 生成密钥掩码。对密文解密后取明文尾部，避免泄露完整密钥；解密失败则给出通用掩码。
     */
    private String mask(String cipher) {
        try {
            String plain = AesUtil.decrypt(cipher, secret);
            if (plain == null || plain.isEmpty()) {
                return null;
            }
            int keep = Math.min(4, plain.length());
            return "****" + plain.substring(plain.length() - keep);
        } catch (Exception e) {
            return "****";
        }
    }

    /**
     * 序列化 options 为 JSON 字符串；为空返回 null。
     */
    private String writeOptions(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "扩展参数序列化失败");
        }
    }

    /**
     * 反序列化 options JSON 字符串；解析失败或为空返回 null。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private AiModelProfile getExisting(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "档案ID不能为空");
        }
        AiModelProfile entity = profileMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "模型档案不存在: " + id);
        }
        return entity;
    }

    private boolean exists(String profileCode, Long excludeId) {
        LambdaQueryWrapper<AiModelProfile> wrapper = new LambdaQueryWrapper<AiModelProfile>()
                .eq(AiModelProfile::getProfileCode, profileCode)
                .ne(excludeId != null, AiModelProfile::getId, excludeId);
        return profileMapper.selectCount(wrapper) > 0;
    }

    private void validate(ModelProfileSaveRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (creating && !StringUtils.hasText(request.getProfileCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "档案编码不能为空");
        }
    }
}
