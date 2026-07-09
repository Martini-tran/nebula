package com.nebula.common.ai.flow;

import com.nebula.common.ai.flow.tool.EchoToolDefinition;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolNodeExecutor} 单测：工具选取、入参映射、产物写回与异常分支。
 *
 * @author nebula
 */
class ToolNodeExecutorTest {

    private final ToolRegistry registry = new ToolRegistry(List.of(new EchoToolDefinition()));

    private final ToolNodeExecutor executor = new ToolNodeExecutor(registry);

    @Test
    void 按映射解析入参并写回产物() {
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("call")
                .setNodeType("TOOL")
                .setInputMapping(Map.of("text", "src"))
                .setOutputKey("echoed");
        node.getNodeConfig().put("toolCode", "echo");
        OrchestrationContext ctx = new OrchestrationContext().put("src", "你好");

        executor.execute(node, ctx);

        assertEquals("你好", ctx.get("echoed"));
    }

    @Test
    void 产物写回键缺省用节点编码() {
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("call")
                .setNodeType("TOOL")
                .setInputMapping(Map.of("text", "src"));
        node.getNodeConfig().put("toolCode", "echo");
        OrchestrationContext ctx = new OrchestrationContext().put("src", "x");

        executor.execute(node, ctx);

        assertEquals("x", ctx.get("call"));
    }

    @Test
    void 缺少toolCode抛异常() {
        FlowNodeDefinition node = new FlowNodeDefinition().setNodeCode("call").setNodeType("TOOL");
        assertThrows(OrchestrationException.class, () -> executor.execute(node, new OrchestrationContext()));
    }

    @Test
    void 工具不存在抛异常() {
        FlowNodeDefinition node = new FlowNodeDefinition().setNodeCode("call").setNodeType("TOOL");
        node.getNodeConfig().put("toolCode", "nope");
        assertThrows(OrchestrationException.class, () -> executor.execute(node, new OrchestrationContext()));
    }

    @Test
    void 注册表按编码索引() {
        assertTrue(registry.contains("echo"));
        assertFalse(registry.contains("nope"));
        assertEquals("echo", registry.find("echo").code());
    }
}
