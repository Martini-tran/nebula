package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ModelProfile;
import com.nebula.common.ai.flow.ModelProfileRepository;
import com.nebula.common.core.crypto.AesUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库版模型档案仓储
 * 以 {@code ai_model_profile} 表为后端，按编码加载并组装为运行时 {@link ModelProfile}：读取时对密文 {@code apiKey}
 * 解密、对 JSON 字符串 {@code options} 反序列化。覆盖 SDK 默认的内存实现
 * {@code InMemoryModelProfileRepository}（由 {@code FlowAutoConfiguration} 以
 * {@code @ConditionalOnMissingBean(ModelProfileRepository.class)} 提供）。
 *
 * <p>密钥由装配方传入（配置项 {@code nebula.ai.profile.secret}），与写入端（管理服务）保持一致即可互通。
 *
 * @author nebula
 */
public class DatabaseModelProfileRepository implements ModelProfileRepository {

    private final AiModelProfileMapper profileMapper;

    private final ObjectMapper objectMapper;

    private final String secret;

    /**
     * @param profileMapper 模型档案 Mapper
     * @param objectMapper  JSON 处理器（反序列化 options）
     * @param secret        apiKey 加解密密钥
     */
    public DatabaseModelProfileRepository(AiModelProfileMapper profileMapper, ObjectMapper objectMapper, String secret) {
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    @Override
    public ModelProfile findByCode(String profileCode) {
        if (profileCode == null || profileCode.isBlank()) {
            return null;
        }
        AiModelProfile entity = profileMapper.selectOne(new LambdaQueryWrapper<AiModelProfile>()
                .eq(AiModelProfile::getProfileCode, profileCode)
                .last("limit 1"));
        if (entity == null) {
            return null;
        }
        return toProfile(entity);
    }

    /**
     * 实体转运行时模型档案（解密 apiKey、反序列化 options）
     *
     * @param entity 数据库实体
     * @return 运行时模型档案
     */
    private ModelProfile toProfile(AiModelProfile entity) {
        ModelProfile profile = new ModelProfile()
                .setProfileCode(entity.getProfileCode())
                .setName(entity.getName())
                .setProvider(entity.getProvider())
                .setBaseUrl(entity.getBaseUrl())
                .setApiKey(decrypt(entity.getApiKey()))
                .setModel(entity.getModel())
                .setTemperature(entity.getTemperature())
                .setMaxTokens(entity.getMaxTokens())
                .setTopP(entity.getTopP())
                .setTimeoutMs(entity.getTimeoutMs());
        Map<String, Object> options = readOptions(entity.getOptions());
        if (options != null) {
            profile.getOptions().putAll(options);
        }
        return profile;
    }

    /**
     * 解密密文 apiKey；为空时透传 null
     */
    private String decrypt(String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return cipher;
        }
        return AesUtil.decrypt(cipher, secret);
    }

    /**
     * 反序列化 options JSON 字符串为 Map；解析失败或为空时返回 null
     */
    private Map<String, Object> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
