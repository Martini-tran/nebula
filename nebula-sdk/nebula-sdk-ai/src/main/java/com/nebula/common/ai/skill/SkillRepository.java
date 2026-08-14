package com.nebula.common.ai.skill;

import java.util.List;

/**
 * 技能定义仓储（SPI）
 *
 * <p>与 {@code ModelProfileRepository} / {@code AgentDefinitionRepository} 同构：SDK 侧只声明接口并给
 * 内存缺省实现，{@code nebula-sdk-ai-flow} 侧提供读 {@code ai_skill} 表的数据库实现覆盖之。
 * 技能是纯 DB 配置（区别于代码定义、启动同步进表的 {@code ai_tool}），后台改完即刻生效、无需发版。
 *
 * @author nebula
 */
public interface SkillRepository {

    /**
     * 按编码取技能定义
     *
     * @param skillCode 技能编码
     * @return 技能定义，不存在返回 null
     */
    SkillDefinition findByCode(String skillCode);

    /**
     * 批量按编码取技能定义。返回顺序与入参无关，由实现按 {@code sortNo} 升序、再按编码排序，
     * 使多技能注入的 system 消息顺序稳定可预期。不存在或已停用的编码被静默忽略。
     *
     * @param skillCodes 技能编码集合
     * @return 技能定义列表，永不为 null
     */
    List<SkillDefinition> findByCodes(List<String> skillCodes);
}
