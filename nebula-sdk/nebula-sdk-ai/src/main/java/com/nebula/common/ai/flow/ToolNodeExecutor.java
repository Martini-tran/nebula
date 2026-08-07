package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具节点执行器
 * 处理 {@code TOOL} 类型节点：从节点 {@code nodeConfig.toolCode} 取目标工具，按节点 {@code inputMapping}
 * 从编排上下文解析入参，调用 {@link ToolRegistry} 中对应 {@link ToolDefinition} 的 {@code invoke}，把产物写回上下文
 * 供下游节点引用。
 *
 * <p>与 {@link PromptNodeExecutor} 一致的约定：{@code inputMapping}（工具入参名 -&gt; 上下文键）为空时不传入参；
 * 产物写回键取 {@code outputKey}，缺省用节点编码。工具的实现/凭证均在代码侧，本执行器只负责「按定义调度」。
 *
 * @author nebula
 */
@Slf4j
public class ToolNodeExecutor implements FlowNodeExecutor {

    /**
     * 节点类型标识
     */
    public static final String TYPE = "TOOL";

    /**
     * {@code nodeConfig} 中工具编码的键
     */
    public static final String CONFIG_TOOL_CODE = "toolCode";

    private final ToolRegistry toolRegistry;

    public ToolNodeExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        String toolCode = resolveToolCode(node);
        ToolDefinition tool = toolRegistry == null ? null : toolRegistry.find(toolCode);
        if (tool == null) {
            throw new OrchestrationException("未找到工具: " + toolCode
                    + "（节点 " + node.getNodeCode() + "）。请确认该工具已在代码中定义并启动同步。");
        }
        if (tool.invocationScopes() == null
                || !tool.invocationScopes().contains(InvocationScope.FLOW_NODE)) {
            throw new OrchestrationException("工具[" + toolCode + "]不允许在流程节点中调用（节点 "
                    + node.getNodeCode() + "）");
        }
        Map<String, Object> params = resolveParams(node, ctx);
        Object result = tool.invoke(params, ctx);
        writeOutput(node, ctx, result);
    }

    /**
     * 从 {@code nodeConfig.toolCode} 取工具编码
     */
    private String resolveToolCode(FlowNodeDefinition node) {
        Map<String, Object> config = node.getNodeConfig();
        Object code = config == null ? null : config.get(CONFIG_TOOL_CODE);
        if (code == null || String.valueOf(code).isBlank()) {
            throw new OrchestrationException("工具节点缺少 nodeConfig.toolCode（节点 " + node.getNodeCode() + "）");
        }
        return String.valueOf(code);
    }

    /**
     * 按输入映射（工具入参名 -&gt; 上下文键）从上下文解析入参；映射为空则传入全部上下文产物。
     */
    private Map<String, Object> resolveParams(FlowNodeDefinition node, OrchestrationContext ctx) {
        Map<String, String> mapping = node.getInputMapping();
        if (mapping == null || mapping.isEmpty()) {
            return new LinkedHashMap<>(ctx.attributes());
        }
        Map<String, Object> params = new LinkedHashMap<>();
        mapping.forEach((paramName, ctxKey) -> {
            if (paramName != null && ctxKey != null) {
                params.put(paramName, ctx.get(ctxKey));
            }
        });
        return params;
    }

    /**
     * 写回产物：outputKey 缺省用节点编码。产物为 null 时不写。
     */
    private void writeOutput(FlowNodeDefinition node, OrchestrationContext ctx, Object result) {
        String key = (node.getOutputKey() == null || node.getOutputKey().isBlank())
                ? node.getNodeCode() : node.getOutputKey();
        if (result == null) {
            log.debug("工具节点[{}] 产物为空，跳过写回", node.getNodeCode());
            return;
        }
        ctx.put(key, result);
    }
}
