package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.skill.SkillDefinition;
import com.nebula.common.ai.skill.SkillRepository;
import com.nebula.common.ai.skill.SkillResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库版技能仓储
 * 以 {@code ai_skill} 表为后端，按编码加载并组装为运行时 {@link SkillDefinition}：反序列化
 * {@code tool_codes}/{@code mcp_server_codes} JSON 数组。覆盖 SDK 默认的内存实现
 * {@code InMemorySkillRepository}（由 {@code FlowAutoConfiguration} 以
 * {@code @ConditionalOnMissingBean(SkillRepository.class)} 提供）。
 *
 * <p>技能是<b>纯 DB 配置</b>：后台改完即刻生效、无需发版，故此处每次调用都实时查库不做缓存
 * （与 {@link DatabaseModelProfileRepository} 一致）。
 *
 * <p>只返回 {@code status=1} 的启用技能——停用即对运行时不可见，是「关掉一个技能」的唯一开关。
 *
 * @author nebula
 */
public class DatabaseSkillRepository implements SkillRepository {

    private final AiSkillMapper skillMapper;

    private final ObjectMapper objectMapper;

    /**
     * @param skillMapper  技能 Mapper
     * @param objectMapper JSON 处理器（反序列化 toolCodes/mcpServerCodes）
     */
    public DatabaseSkillRepository(AiSkillMapper skillMapper, ObjectMapper objectMapper) {
        this.skillMapper = skillMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public SkillDefinition findByCode(String skillCode) {
        if (skillCode == null || skillCode.isBlank()) {
            return null;
        }
        AiSkill entity = skillMapper.selectOne(new LambdaQueryWrapper<AiSkill>()
                .eq(AiSkill::getSkillCode, skillCode)
                .eq(AiSkill::getStatus, 1)
                .last("limit 1"));
        return entity == null ? null : toDefinition(entity);
    }

    /**
     * 批量取技能：一次 {@code in} 查询而非逐个查库，避免 N 个技能产生 N 次往返。
     */
    @Override
    public List<SkillDefinition> findByCodes(List<String> skillCodes) {
        if (skillCodes == null || skillCodes.isEmpty()) {
            return List.of();
        }
        List<AiSkill> entities = skillMapper.selectList(new LambdaQueryWrapper<AiSkill>()
                .in(AiSkill::getSkillCode, skillCodes)
                .eq(AiSkill::getStatus, 1));
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<SkillDefinition> definitions = new ArrayList<>(entities.size());
        entities.forEach(entity -> definitions.add(toDefinition(entity)));
        definitions.sort(SkillResolver.ORDER);
        return definitions;
    }

    /**
     * 实体转运行时技能定义（反序列化 toolCodes/mcpServerCodes）
     *
     * @param entity 数据库实体
     * @return 运行时技能定义
     */
    private SkillDefinition toDefinition(AiSkill entity) {
        SkillDefinition definition = new SkillDefinition()
                .setSkillCode(entity.getSkillCode())
                .setName(entity.getName())
                .setDescription(entity.getDescription())
                .setInstructions(entity.getInstructions())
                .setTriggerType(entity.getTriggerType() == null
                        ? SkillDefinition.TRIGGER_MANUAL : entity.getTriggerType())
                .setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo())
                .setStatus(entity.getStatus() == null ? 1 : entity.getStatus())
                .setRemark(entity.getRemark());
        definition.setToolCodes(readCodes(entity.getToolCodes()));
        definition.setMcpServerCodes(readCodes(entity.getMcpServerCodes()));
        return definition;
    }

    /**
     * 反序列化编码数组 JSON 字符串；解析失败或为空时返回空列表（不返回 null，调用方可直接遍历）
     */
    private List<String> readCodes(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> codes = objectMapper.readValue(json, new TypeReference<ArrayList<String>>() {
            });
            return codes == null ? new ArrayList<>() : codes;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
