package com.nebula.common.ai.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.util.AiTemplateUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结束节点执行器（END）
 * 流程唯一出口：把最终输出收敛为**固定格式 JSON**。读节点 {@code nodeConfig.end.outputJson}——
 * 一个 JSON 对象模板（键=输出字段，字符串值里用 {@code {{outputKey}}} 引用上游产物），用编排上下文
 * 变量渲染每个占位后，得到最终结果对象。产出双写：
 * <ol>
 *   <li><b>逐键写回上下文</b>：最终 JSON 的每个 kv 落进 context（保存上下文、供审计/回放/上层读取）；</li>
 *   <li><b>整体写到 {@link #OUTPUT_KEY}</b>：完整最终 JSON 对象存入约定键 {@code __output}，上层（REST/
 *       AgentEngine）读它拿"流程最终结果"。</li>
 * </ol>
 *
 * <p>模板为空 / 非法 JSON 时，不产出结构化结果（记 warn），不阻断流程终止——END 到达即流程成功。
 *
 * @author nebula
 */
@Slf4j
public class EndNodeExecutor implements FlowNodeExecutor {

    /**
     * 节点类型标识
     */
    public static final String TYPE = "END";

    /**
     * 上下文保留键：流程最终结果（END 渲染出的固定 JSON 对象）。上层据此取最终输出。
     */
    public static final String OUTPUT_KEY = "__output";

    /**
     * {@code nodeConfig} 中 END 配置段的键
     */
    private static final String CONFIG_END = "end";

    /**
     * END 配置段中输出 JSON 模板文本的键
     */
    private static final String CONFIG_OUTPUT_JSON = "outputJson";

    private final ObjectMapper objectMapper;

    public EndNodeExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        String template = resolveTemplate(node);
        if (template == null || template.isBlank()) {
            log.debug("结束节点[{}] 未配置输出模板，无结构化产出", node.getNodeCode());
            return;
        }
        // 用上下文全部产物作为变量池渲染 {{outputKey}} 占位（AiTemplateUtils 兼容 {{}} 与 #{}）
        Map<String, Object> variables = new LinkedHashMap<>(ctx.attributes());
        String rendered = AiTemplateUtils.render(template, variables);

        Map<String, Object> output;
        try {
            output = objectMapper.readValue(rendered, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("结束节点[{}] 输出模板渲染后不是合法 JSON 对象，跳过结构化产出: {}",
                    node.getNodeCode(), e.getMessage());
            return;
        }

        // ① 逐键写回上下文（保存上下文）
        output.forEach(ctx::put);
        // ② 整体最终结果写到约定键（上层读取最终输出）
        ctx.put(OUTPUT_KEY, output);
        log.info("结束节点[{}] 产出最终结果字段 {}", node.getNodeCode(), output.keySet());
    }

    /**
     * 取 END 输出模板：{@code nodeConfig.end.outputJson}
     */
    @SuppressWarnings("unchecked")
    private String resolveTemplate(FlowNodeDefinition node) {
        Map<String, Object> config = node.getNodeConfig();
        if (config == null) {
            return null;
        }
        Object end = config.get(CONFIG_END);
        if (!(end instanceof Map<?, ?> endMap)) {
            return null;
        }
        Object json = ((Map<String, Object>) endMap).get(CONFIG_OUTPUT_JSON);
        return json == null ? null : String.valueOf(json);
    }
}
