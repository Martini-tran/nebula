package com.nebula.manager.ai.copilot.tool;

import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 工具：列出流程可用的节点类型及语义。
 * 供"流程设计助手"在生成流程前获取权威节点类型清单，避免臆造不存在的 nodeType。
 * 类型集从容器内已注册的 {@link FlowNodeExecutor} 动态收集（与 {@code FlowAdminController.nodeTypes} 同源），
 * 语义描述在本工具内维护一份完整清单（比 controller 的两项映射更全，专供 LLM 理解）。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class ListNodeTypesToolDefinition implements ToolDefinition {

    /**
     * Copilot 工具统一分类，用于在 {@code ai_tool} 表与前端工具面板中与业务工具区分、过滤。
     */
    public static final String COPILOT_CATEGORY = "copilot";

    /**
     * 节点类型 → 面向 LLM 的语义描述。缺失的类型回退用 type 本身。
     */
    private static final Map<String, String> NODE_SEMANTICS = buildSemantics();

    /**
     * 容器内全部节点执行器，用于动态收集可用节点类型（新增执行器无需改此处）。
     */
    private final List<FlowNodeExecutor> nodeExecutors;

    @Override
    public String code() {
        return "list_node_types";
    }

    @Override
    public String name() {
        return "列出流程节点类型";
    }

    @Override
    public String description() {
        return "列出流程可用的节点类型（type）及其语义说明。生成流程前应先调用，以获得权威的 nodeType 取值，避免使用不存在的类型。";
    }

    @Override
    public String category() {
        return COPILOT_CATEGORY;
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    @Override
    public int sortNo() {
        return 10;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        List<Map<String, Object>> types = new ArrayList<>();
        nodeExecutors.stream()
                .map(FlowNodeExecutor::type)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .forEach(type -> {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("type", type);
                    one.put("description", NODE_SEMANTICS.getOrDefault(type, type));
                    types.add(one);
                });
        return types;
    }

    /**
     * 构造节点语义清单。覆盖当前引擎支持的全部节点类型；结构性节点（START/END/IF/JOIN/LOOP）
     * 未必有独立 executor（可能走 NoOp / 编图期闭包驱动），故这里静态声明以保证 LLM 完整可见。
     */
    private static Map<String, String> buildSemantics() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("PROMPT", "提示词节点：渲染 promptTemplate（#{var} 引用上下文）后调用大模型，产物写回 outputKey。最常用。");
        m.put("TOOL", "工具节点：调用一个已注册工具（需在 nodeConfig.toolCode 指定工具编码），把工具产物写回上下文。");
        m.put("AGENT", "子 Agent 节点：递归调用另一个已定义的 Agent（nodeConfig.refAgentCode），受最大深度治理。");
        m.put("AGENT_REACT", "ReAct 里程碑节点：给模型一批白名单工具（nodeConfig.toolCodes），由模型自主多轮选调不同工具完成一个里程碑。");
        m.put("START", "开始节点：流程入口，DAG 建议每图恰好一个。可在 nodeConfig.inputs 声明入参。");
        m.put("END", "结束节点：流程出口，DAG 建议每图恰好一个。可在 nodeConfig.end.outputJson 定义最终产物模板。");
        m.put("IF", "条件分支节点：靠各条出边的 conditionExpr（SpEL）对上下文求值决定走向；空表达式的边作为 default。");
        m.put("JOIN", "汇聚节点：并行分支的汇合点，入度归零后继续。");
        m.put("LOOP", "循环容器节点：内嵌子图循环（FOREACH/COUNT），成员节点写在 nodeConfig.members。");
        return m;
    }
}
