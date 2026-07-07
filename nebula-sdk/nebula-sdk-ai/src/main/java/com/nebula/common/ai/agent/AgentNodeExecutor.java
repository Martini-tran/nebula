package com.nebula.common.ai.agent;

import com.nebula.common.ai.flow.EndNodeExecutor;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Agent 节点执行器（递归子 Agent，阶段 3）
 * v1 核心卖点：Flow 中的 {@code nodeType=AGENT} 节点可引用另一个 Agent（子 Flow），从而无限递归。执行时：
 * <ol>
 *   <li><b>Input Mapping</b>：把父 context 的字段映射为子 Agent 的入参（默认<b>不共享 Memory</b>，只传映射入参）；</li>
 *   <li>递归调 {@link AgentEngine#run}：子 Agent 独立建实例、独立 Import/Export 自己的 Memory、独立落
 *       {@code parent_instance_id}/{@code parent_node_code}；</li>
 *   <li><b>Output Mapping</b>：把子 Agent 的产物按映射写回父 context。</li>
 * </ol>
 *
 * <p><b>治理</b>：用父 context 的调用栈键 {@link #CALL_STACK_KEY} 追踪递归链——
 * ① 栈深 &ge; maxAgentDepth（默认 8，可被节点 {@code nodeConfig.maxAgentDepth} 覆盖）→ 拒绝，防无限递归；
 * ② 栈中已含 refAgentCode（A→B→A）→ 拒绝，防循环引用。
 *
 * <p><b>循环依赖打破</b>：本执行器需 {@link AgentEngine}，而 AgentEngine 编图的工厂又需执行器列表——
 * 用 {@link Supplier}（Spring 侧传 {@code ObjectProvider::getObject}）延迟取 AgentEngine，打破构造期循环。
 *
 * <p><b>阶段边界</b>：本阶段递归子 Agent <b>只同步跑完</b>；子若挂起（返回 SUSPENDED）则抛出，"子挂起→父挂起→
 * 子完成回调唤醒父"的异步父子生命周期编织留后续。
 *
 * @author nebula
 */
public class AgentNodeExecutor implements FlowNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentNodeExecutor.class);

    /**
     * 节点类型标识
     */
    public static final String TYPE = "AGENT";

    /**
     * 父 context 中承载递归调用链（List&lt;agentCode&gt;）的约定键，用于深度与循环引用治理
     */
    public static final String CALL_STACK_KEY = "__agentCallStack";

    /**
     * 默认递归深度上限，可被节点 {@code nodeConfig.maxAgentDepth} 覆盖
     */
    private static final int DEFAULT_MAX_DEPTH = 8;

    private final Supplier<AgentEngine> engineSupplier;

    private final AgentDefinitionRepository definitionRepository;

    public AgentNodeExecutor(Supplier<AgentEngine> engineSupplier,
                             AgentDefinitionRepository definitionRepository) {
        this.engineSupplier = engineSupplier;
        this.definitionRepository = definitionRepository;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        Map<String, Object> config = node.getNodeConfig();
        String refAgentCode = str(config.get("refAgentCode"));
        if (refAgentCode == null || refAgentCode.isBlank()) {
            throw new OrchestrationException("AGENT 节点[" + node.getNodeCode() + "]缺少 refAgentCode");
        }
        if (definitionRepository == null) {
            throw new OrchestrationException("未配置 AgentDefinitionRepository，AGENT 节点无法解析子 Agent");
        }

        // 取当前递归调用栈（父 context 传递），做深度与循环引用治理
        List<String> callStack = ctx.get(CALL_STACK_KEY, List.class);
        List<String> stack = callStack == null ? new ArrayList<>() : new ArrayList<>(callStack);
        // 深度上限优先取本流程 flow.maxAgentDepth（AgentEngine 写入 context），再回退节点配置，最后默认
        int maxDepth = intVal(ctx.get(AgentEngine.MAX_AGENT_DEPTH_KEY),
                intVal(config.get("maxAgentDepth"), DEFAULT_MAX_DEPTH));
        if (stack.size() >= maxDepth) {
            throw new OrchestrationException("AGENT 节点[" + node.getNodeCode() + "]递归深度超上限("
                    + maxDepth + ")，调用链=" + stack);
        }
        if (stack.contains(refAgentCode)) {
            throw new OrchestrationException("AGENT 节点[" + node.getNodeCode() + "]检测到循环引用："
                    + refAgentCode + " 已在调用链 " + stack + " 中");
        }

        AgentDefinition childDef = definitionRepository.find(refAgentCode);
        if (childDef == null) {
            throw new OrchestrationException("AGENT 节点[" + node.getNodeCode()
                    + "]引用的子 Agent 不存在: " + refAgentCode);
        }

        // Input Mapping：父 ctx 键 → 子入参键（缺省不共享 Memory，只传映射入参）
        Map<String, Object> childInputs = mapInputs(config.get("inputMapping"), ctx);
        // 把当前 refAgentCode 压栈后透传给子（子若再递归会看到完整链，治理跨层生效）
        stack.add(refAgentCode);
        childInputs.put(CALL_STACK_KEY, stack);

        // 递归执行子 Agent：子独立建实例 + 独立 Import/Export + 落 parent 关联
        String parentInstanceId = ctx.getString(AgentEngine.CURRENT_INSTANCE_KEY);
        AgentEngine engine = engineSupplier == null ? null : engineSupplier.get();
        if (engine == null) {
            throw new OrchestrationException("AgentEngine 未就绪，AGENT 节点无法递归执行子 Agent");
        }
        log.info("AGENT 节点[{}] 递归执行子 Agent[{}]，深度={}", node.getNodeCode(), refAgentCode, stack.size());
        OrchestrationContext childCtx = engine.run(childDef, childInputs,
                ctx.getUserId(), ctx.getConversationId(), parentInstanceId, node.getNodeCode());

        // 子挂起则抛出（阶段 3 递归只同步跑完，异步父子编织留后续）
        if (childCtx.contains(com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator.SUSPENDED_KEY)) {
            throw new OrchestrationException("AGENT 节点[" + node.getNodeCode() + "]子 Agent["
                    + refAgentCode + "]挂起——子挂起→父挂起的异步编织本阶段未支持");
        }

        // Output Mapping：子产物 → 父 ctx 键
        mapOutputs(config.get("outputMapping"), childCtx, ctx);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapInputs(Object inputMapping, OrchestrationContext parent) {
        Map<String, Object> childInputs = new java.util.LinkedHashMap<>();
        if (inputMapping instanceof Map<?, ?> mapping) {
            for (Map.Entry<?, ?> e : mapping.entrySet()) {
                // key=子入参键，value=父 ctx 键
                String childKey = String.valueOf(e.getKey());
                String parentKey = String.valueOf(e.getValue());
                Object v = parent.get(parentKey);
                if (v != null) {
                    childInputs.put(childKey, v);
                }
            }
        }
        return childInputs;
    }

    private void mapOutputs(Object outputMapping, OrchestrationContext child, OrchestrationContext parent) {
        // 未配 Output Mapping（缺失/非 Map/空映射）时兜底：把子 END 渲染的完整结果 __output 整体
        // 带回父 context 同名键，避免子产物完全丢失。父级据此仍能引用 {{__output}} 或按需再取。
        if (!(outputMapping instanceof Map<?, ?> mapping) || mapping.isEmpty()) {
            Object whole = child.get(EndNodeExecutor.OUTPUT_KEY);
            if (whole != null) {
                parent.put(EndNodeExecutor.OUTPUT_KEY, whole);
            }
            return;
        }
        for (Map.Entry<?, ?> e : mapping.entrySet()) {
            // key=父 ctx 键，value=子产物键
            String parentKey = String.valueOf(e.getKey());
            String childKey = String.valueOf(e.getValue());
            // 两种取法：value=__output 取子 END 渲染的完整 JSON 对象（整体透传）；
            // 否则按字段名取——子 EndNodeExecutor 已把 END 输出 JSON 逐键写回子 context，
            // 故 value 直接写 END JSON 的顶层字段名（如 answer）即可取到对应值。
            Object v = child.get(childKey);
            if (v != null) {
                parent.put(parentKey, v);
            }
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
