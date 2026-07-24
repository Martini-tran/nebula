package com.nebula.manager.ai.copilot.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.ai.agent.AiAgentAdminService;
import com.nebula.manager.dto.AgentSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 工具：基于一个已存在的流程派生一个 Agent 定义（ai_agent）。
 * "流程设计助手"在生成流程后可据用户意图调用本工具派生 Agent。需先有 flowCode（通常紧接 generate_flow）。
 * 校验失败不抛异常，返回 {@code {ok:false,error}} 让工具循环回灌给模型自愈（如换一个 agentCode）。
 *
 * @author nebula
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeriveAgentToolDefinition implements ToolDefinition {

    /**
     * flowVersion 缺省值：与 generate_flow 生成流程的默认版本一致。
     */
    private static final int DEFAULT_FLOW_VERSION = 1;

    /**
     * 惰性获取 {@link AiAgentAdminService}：其实现链（AgentEngine → … → ToolNodeExecutor → ToolRegistry）
     * 会反向聚合本 Bean，构造期强注入会形成 Spring Bean 循环依赖。工具仅在 {@link #invoke} 运行期才需要它，
     * 故用 {@link ObjectProvider} 延迟到调用时解析，打断构造期的回边。
     */
    private final ObjectProvider<AiAgentAdminService> aiAgentAdminServiceProvider;

    @Override
    public String code() {
        return "derive_agent";
    }

    @Override
    public String name() {
        return "派生 Agent";
    }

    @Override
    public String description() {
        return "基于一个已存在的流程（flowCode）派生一个 Agent 定义（ai_agent），成功返回 agentCode 与 id。"
                + "需先有 flowCode（通常紧接 generate_flow 生成）。agentCode 是跨版本稳定的记忆隔离键，须唯一。";
    }

    @Override
    public String category() {
        return ListNodeTypesToolDefinition.COPILOT_CATEGORY;
    }

    @Override
    public int sortNo() {
        return 21;
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("agentCode", "flowCode"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agentCode", str("Agent 编码，全局唯一（英文/下划线），是记忆隔离键，创建后不建议改"));
        props.put("name", str("Agent 名称"));
        props.put("description", str("Agent 描述"));
        props.put("flowCode", str("引用的流程编码（通常是刚由 generate_flow 生成的流程）"));
        props.put("flowVersion", intWithDefault("引用的流程版本，默认 1", DEFAULT_FLOW_VERSION));
        props.put("inputSchema", str("输入契约 JSON Schema 文本，可空"));
        props.put("outputSchema", str("输出契约 JSON Schema 文本，可空"));
        props.put("memoryConfig", str("记忆配置 JSON 文本（enabled / 导入键 / 导出策略），可空"));
        props.put("defaultProfileCode", str("默认模型档案编码，可空"));
        schema.put("properties", props);
        return schema;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        if (params == null || params.isEmpty()) {
            return fail("入参为空，请提供 agentCode 与 flowCode");
        }
        String agentCode = str(params.get("agentCode"));
        String flowCode = str(params.get("flowCode"));
        if (isBlank(agentCode)) {
            return fail("agentCode 不能为空");
        }
        if (isBlank(flowCode)) {
            return fail("flowCode 不能为空");
        }

        AgentSaveRequest request = new AgentSaveRequest();
        request.setAgentCode(agentCode);
        request.setName(str(params.get("name")));
        request.setDescription(str(params.get("description")));
        request.setFlowCode(flowCode);
        Integer flowVersion = toInt(params.get("flowVersion"));
        request.setFlowVersion(flowVersion != null && flowVersion > 0 ? flowVersion : DEFAULT_FLOW_VERSION);
        request.setInputSchema(str(params.get("inputSchema")));
        request.setOutputSchema(str(params.get("outputSchema")));
        request.setMemoryConfig(str(params.get("memoryConfig")));
        request.setDefaultProfileCode(str(params.get("defaultProfileCode")));

        Long id;
        try {
            id = aiAgentAdminServiceProvider.getObject().create(request);
        } catch (BizException e) {
            return fail("派生失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("derive_agent 落库异常: {}", e.getMessage());
            return fail("派生异常: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("agentCode", agentCode);
        result.put("id", id);
        result.put("name", request.getName());
        return result;
    }

    private Map<String, Object> fail(String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", error);
        return m;
    }

    private static Map<String, Object> str(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> intWithDefault(String description, int def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", description);
        m.put("default", def);
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
