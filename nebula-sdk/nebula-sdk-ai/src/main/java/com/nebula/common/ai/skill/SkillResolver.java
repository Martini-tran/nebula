package com.nebula.common.ai.skill;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.util.AiTemplateUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 技能解析器
 *
 * <p>把「两层技能声明」收敛为一次模型调用所需的两样东西：<b>注入的 system 指令</b> 与 <b>并入的工具白名单</b>。
 *
 * <p><b>两层绑定</b>（并集，Agent 级在前、节点级在后）：
 * <ol>
 *   <li><b>Agent 级</b>：{@code ai_agent.skill_codes}，由 {@code AgentEngine} 写入编排上下文
 *       {@link com.nebula.common.ai.orchestration.ContextKeys.Agent#SKILLS}，该 Agent 全部节点共享；</li>
 *   <li><b>节点级</b>：{@code nodeConfig.skillCodes}，仅该节点生效，用于在基础技能上追加。</li>
 * </ol>
 * 同一技能编码在两层同时出现时只装载一次（按首次出现位置去重），避免指令重复注入浪费上下文。
 *
 * <p><b>工具白名单是并集而非替换</b>：技能绑定的 {@code toolCodes} 与节点原有的
 * {@code nodeConfig.toolCodes} 合并。技能只增不减可用工具，不会因为挂了技能反而失去原有工具；
 * 最终仍由 {@code DefaultToolCallingService} 按「已注册 + 已启用」二次过滤，技能无法绕过工具治理。
 *
 * @author nebula
 */
@Slf4j
public final class SkillResolver {

    /**
     * 技能装载顺序：sortNo 升序，再按编码排序，使多技能注入顺序稳定可预期。
     */
    public static final Comparator<SkillDefinition> ORDER = Comparator
            .comparingInt((SkillDefinition s) -> s.getSortNo() == null ? 0 : s.getSortNo())
            .thenComparing(s -> s.getSkillCode() == null ? "" : s.getSkillCode());

    private final SkillRepository skillRepository;

    public SkillResolver(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * 解析本次调用命中的技能：合并 Agent 级与节点级声明后去重、查库、过滤停用项。
     *
     * @param agentSkillCodes Agent 级技能编码（可空）
     * @param nodeSkillCodes  节点级技能编码（可空）
     * @return 命中的技能定义列表（已按 {@link #ORDER} 排序），永不为 null
     */
    public List<SkillDefinition> resolve(List<String> agentSkillCodes, List<String> nodeSkillCodes) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addAll(merged, agentSkillCodes);
        addAll(merged, nodeSkillCodes);
        if (merged.isEmpty() || skillRepository == null) {
            return List.of();
        }
        List<SkillDefinition> resolved = skillRepository.findByCodes(new ArrayList<>(merged));
        if (resolved == null || resolved.isEmpty()) {
            return List.of();
        }
        if (resolved.size() < merged.size()) {
            LinkedHashSet<String> missing = new LinkedHashSet<>(merged);
            resolved.forEach(s -> missing.remove(s.getSkillCode()));
            if (!missing.isEmpty()) {
                log.warn("引用的技能不存在或已停用，已忽略: {}", missing);
            }
        }
        return resolved;
    }

    /**
     * 渲染技能指令为 system 消息文本。空正文技能被跳过（只绑工具、不注入指令是合法用法）。
     *
     * @param skills    命中的技能
     * @param variables 模板变量池（编排上下文产物），可空
     * @return system 文本列表，与 {@code skills} 同序，永不为 null
     */
    public List<String> renderInstructions(List<SkillDefinition> skills, Map<String, Object> variables) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (SkillDefinition skill : skills) {
            if (!skill.hasInstructions()) {
                continue;
            }
            String rendered = AiTemplateUtils.render(skill.getInstructions(), variables);
            if (rendered != null && !rendered.isBlank()) {
                texts.add(rendered);
            }
        }
        return texts;
    }

    /**
     * 合并工具白名单：节点原有 {@code toolCodes} 在前，技能绑定的工具追加在后，去重保序。
     *
     * @param nodeToolCodes 节点原有工具白名单（可空）
     * @param skills        命中的技能（可空）
     * @return 合并后的工具编码列表，永不为 null
     */
    public List<String> mergeToolCodes(List<String> nodeToolCodes, List<SkillDefinition> skills) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addAll(merged, nodeToolCodes);
        if (skills != null) {
            skills.forEach(skill -> addAll(merged, skill.getToolCodes()));
        }
        return new ArrayList<>(merged);
    }

    /**
     * 从编排上下文读取 Agent 级技能编码（由 {@code AgentEngine} 写入）。
     *
     * @param ctx 编排上下文，可空
     * @return 技能编码列表，永不为 null
     */
    @SuppressWarnings("unchecked")
    public static List<String> agentSkillCodes(OrchestrationContext ctx) {
        Object raw = ctx == null ? null : ctx.get(com.nebula.common.ai.orchestration.ContextKeys.Agent.SKILLS);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return toStringList((List<Object>) list);
    }

    /**
     * 从节点配置读取节点级技能编码。
     *
     * @param nodeConfig 节点配置，可空
     * @param configKey  技能编码在配置中的键
     * @return 技能编码列表，永不为 null
     */
    @SuppressWarnings("unchecked")
    public static List<String> nodeSkillCodes(Map<String, Object> nodeConfig, String configKey) {
        Object raw = nodeConfig == null ? null : nodeConfig.get(configKey);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return toStringList((List<Object>) list);
    }

    private static List<String> toStringList(List<Object> list) {
        List<String> codes = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                codes.add(String.valueOf(item).trim());
            }
        }
        return codes;
    }

    private static void addAll(LinkedHashSet<String> target, List<String> source) {
        if (source == null) {
            return;
        }
        for (String code : source) {
            if (code != null && !code.isBlank()) {
                target.add(code.trim());
            }
        }
    }
}
