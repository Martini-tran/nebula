package com.nebula.common.ai.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存技能仓储（缺省实现）
 * 无数据库时的兜底：可由测试/嵌入式场景手工注册技能。生产环境由 {@code nebula-sdk-ai-flow} 的
 * 数据库实现覆盖（读 {@code ai_skill} 表）。
 *
 * @author nebula
 */
public class InMemorySkillRepository implements SkillRepository {

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();

    public InMemorySkillRepository() {
    }

    public InMemorySkillRepository(List<SkillDefinition> definitions) {
        if (definitions != null) {
            definitions.forEach(this::register);
        }
    }

    /**
     * 注册/覆盖一个技能定义
     *
     * @param definition 技能定义
     */
    public void register(SkillDefinition definition) {
        if (definition != null && definition.getSkillCode() != null && !definition.getSkillCode().isBlank()) {
            skills.put(definition.getSkillCode(), definition);
        }
    }

    @Override
    public SkillDefinition findByCode(String skillCode) {
        return skillCode == null ? null : skills.get(skillCode);
    }

    @Override
    public List<SkillDefinition> findByCodes(List<String> skillCodes) {
        if (skillCodes == null || skillCodes.isEmpty()) {
            return List.of();
        }
        List<SkillDefinition> found = new ArrayList<>();
        for (String code : skillCodes) {
            SkillDefinition definition = findByCode(code);
            if (definition != null && definition.isEnabled()) {
                found.add(definition);
            }
        }
        found.sort(SkillResolver.ORDER);
        return found;
    }
}
