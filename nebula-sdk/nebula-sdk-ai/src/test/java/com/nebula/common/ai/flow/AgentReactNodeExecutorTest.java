package com.nebula.common.ai.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentReactNodeExecutor} 单测：工具白名单透传、模型参数覆盖链、ReAct 产物写回与截断告警。
 *
 * <p>本类同时钉住《计划-执行节点设计》附录 A 依赖的两条契约：
 * <ul>
 *   <li>{@code nodeConfig.toolCodes}（A.1 ③ 执行节点）确实被当作白名单透传给工具调用闭环；</li>
 *   <li>JSON 产物<b>逐键展开</b>进上下文，使 {@code get('hasPending')} 这类 guard（A.3）能读到——
 *       这是"执行 → IF → 回跳执行"成环结构成立的前提。</li>
 * </ul>
 *
 * @author nebula
 */
class AgentReactNodeExecutorTest {

    private final StubToolCallingService toolCalling = new StubToolCallingService();

    private final InMemoryModelProfileRepository profiles = new InMemoryModelProfileRepository();

    private final AgentReactNodeExecutor executor =
            new AgentReactNodeExecutor(toolCalling, profiles, new ObjectMapper());

    @Test
    void 节点类型为AGENT_REACT() {
        assertEquals("AGENT_REACT", executor.type());
    }

    @Test
    void 渲染模板并把工具白名单透传给闭环() {
        toolCalling.responseContent = "里程碑完成";
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec")
                .setNodeType("AGENT_REACT")
                .setPromptTemplate("完成里程碑：{{plan}}")
                .setOutputKey("execResult");
        node.getNodeConfig().put("toolCodes", List.of("search", "http_request"));
        OrchestrationContext ctx = new OrchestrationContext().put("plan", "调研主题");

        executor.execute(node, ctx);

        assertEquals("完成里程碑：调研主题", toolCalling.lastRequest.getPrompt());
        assertEquals(List.of("search", "http_request"), toolCalling.lastToolCodes);
        assertEquals("里程碑完成", ctx.get("execResult"));
    }

    @Test
    void 工具上下文携带用户与会话身份() {
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setPromptTemplate("x");
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.setUserId("u-1");
        ctx.setConversationId("c-1");

        executor.execute(node, ctx);

        assertNotNull(toolCalling.lastToolContext);
        assertEquals("u-1", toolCalling.lastToolContext.userId());
        assertEquals("c-1", toolCalling.lastToolContext.conversationId());
    }

    @Test
    void 空白名单退化为普通对话() {
        FlowNodeDefinition node = new FlowNodeDefinition().setNodeCode("exec").setPromptTemplate("x");

        executor.execute(node, new OrchestrationContext());

        assertNotNull(toolCalling.lastToolCodes);
        assertTrue(toolCalling.lastToolCodes.isEmpty());
    }

    @Test
    void 白名单过滤空白项() {
        FlowNodeDefinition node = new FlowNodeDefinition().setNodeCode("exec").setPromptTemplate("x");
        node.getNodeConfig().put("toolCodes", java.util.Arrays.asList("search", "", null, "  "));

        executor.execute(node, new OrchestrationContext());

        assertEquals(List.of("search"), toolCalling.lastToolCodes);
    }

    @Test
    void 参数优先级_节点覆盖档案() {
        profiles.register(new ModelProfile().setProfileCode("p1").setModel("m-base").setTemperature(0.2)
                .setOptions(Map.of("seed", 1)));
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setPromptTemplate("hi").setProfileCode("p1")
                .setTemperature(0.9).setOptions(Map.of("response_format", "json"));

        executor.execute(node, new OrchestrationContext());

        assertEquals("m-base", toolCalling.lastRequest.getModel());
        assertEquals(0.9, toolCalling.lastRequest.getTemperature());
        assertEquals(1, toolCalling.lastRequest.getOptions().get("seed"));
        assertEquals("json", toolCalling.lastRequest.getOptions().get("response_format"));
    }

    @Test
    void 系统提示词存在时改用消息列表() {
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setSystemPrompt("你负责完成里程碑：{{role}}").setPromptTemplate("开始");
        OrchestrationContext ctx = new OrchestrationContext().put("role", "写作");

        executor.execute(node, ctx);

        assertNull(toolCalling.lastRequest.getPrompt());
        assertEquals(2, toolCalling.lastRequest.getMessages().size());
        assertEquals("你负责完成里程碑：写作", toolCalling.lastRequest.getMessages().get(0).get("content"));
        assertEquals("开始", toolCalling.lastRequest.getMessages().get(1).get("content"));
    }

    @Test
    void JSON产物逐键展开供IF成环guard读取() {
        // 模拟执行节点终轮产物：更新后的 plan（首项已完成）+ hasPending 布尔 + 业务产物
        toolCalling.responseContent = "{\"plan\":[{\"id\":1,\"milestone\":\"调研\",\"done\":true},"
                + "{\"id\":2,\"milestone\":\"写初稿\",\"done\":false}],"
                + "\"hasPending\":true,\"articleBody\":\"正文\"}";
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setPromptTemplate("x").setOutputMode("JSON").setOutputKey("execRaw");
        OrchestrationContext ctx = new OrchestrationContext();

        executor.execute(node, ctx);

        // 逐键展开：guard `get('hasPending') == true` 与下游节点得以直接读到
        assertEquals(true, ctx.get("hasPending"));
        assertEquals("正文", ctx.get("articleBody"));
        assertTrue(ctx.get("plan") instanceof List);
        assertEquals(2, ((List<?>) ctx.get("plan")).size());
        // 原文同时保留在 outputKey 下
        assertTrue(ctx.contains("execRaw"));
    }

    @Test
    void JSON解析失败退化为整段写入不抛异常() {
        toolCalling.responseContent = "这不是 JSON";
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setPromptTemplate("x").setOutputMode("JSON").setOutputKey("execRaw");
        OrchestrationContext ctx = new OrchestrationContext();

        executor.execute(node, ctx);

        assertEquals("这不是 JSON", ctx.get("execRaw"));
        assertFalse(ctx.contains("hasPending"));
    }

    @Test
    void 产物写回键缺省用节点编码() {
        toolCalling.responseContent = "结果";
        FlowNodeDefinition node = new FlowNodeDefinition().setNodeCode("exec").setPromptTemplate("x");

        OrchestrationContext ctx = new OrchestrationContext();
        executor.execute(node, ctx);

        assertEquals("结果", ctx.get("exec"));
    }

    @Test
    void 闭环截断时仍写回终轮产物() {
        toolCalling.truncated = true;
        toolCalling.responseContent = "未完成的中间结果";
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setPromptTemplate("x").setOutputKey("execResult");
        OrchestrationContext ctx = new OrchestrationContext();

        executor.execute(node, ctx);

        // 达迭代上限只 warn 不抛异常：节点仍 SUCCESS，产物落 context 供后续 guard 判断
        assertEquals("未完成的中间结果", ctx.get("execResult"));
    }

    @Test
    void 入参映射重命名上下文键() {
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("exec").setPromptTemplate("目标：{{goal}}")
                .setInputMapping(Map.of("goal", "topic"));
        OrchestrationContext ctx = new OrchestrationContext().put("topic", "30 天 Java 进阶");

        executor.execute(node, ctx);

        assertEquals("目标：30 天 Java 进阶", toolCalling.lastRequest.getPrompt());
    }
}
