package com.nebula.common.ai.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PromptNodeExecutor} 单测：模板渲染、参数优先级合并、产物写回。
 *
 * @author nebula
 */
class PromptNodeExecutorTest {

    private final StubAiService aiService = new StubAiService();

    private final InMemoryModelProfileRepository profiles = new InMemoryModelProfileRepository();

    private final PromptNodeExecutor executor = new PromptNodeExecutor(aiService, profiles, new ObjectMapper());

    @Test
    void 渲染模板并写回产物() {
        aiService.responseContent = "正文内容";
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("write")
                .setPromptTemplate("围绕 #{topic} 写一段")
                .setOutputKey("article");
        OrchestrationContext ctx = new OrchestrationContext().put("topic", "Spring");

        executor.execute(node, ctx);

        assertEquals("围绕 Spring 写一段", aiService.lastRequest.getPrompt());
        assertEquals("正文内容", ctx.get("article"));
    }

    @Test
    void 参数优先级_节点覆盖档案() {
        profiles.register(new ModelProfile().setProfileCode("p1").setModel("m-base").setTemperature(0.2)
                .setOptions(Map.of("seed", 1)));
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("n").setPromptTemplate("hi").setProfileCode("p1")
                .setTemperature(0.9).setOptions(Map.of("response_format", "json"));

        executor.execute(node, new OrchestrationContext());

        assertEquals("m-base", aiService.lastRequest.getModel());
        assertEquals(0.9, aiService.lastRequest.getTemperature());
        assertEquals(1, aiService.lastRequest.getOptions().get("seed"));
        assertEquals("json", aiService.lastRequest.getOptions().get("response_format"));
    }

    @Test
    void JSON产物模式逐键展开() {
        aiService.responseContent = "{\"title\":\"标题\",\"score\":3}";
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("gen").setPromptTemplate("x").setOutputMode("JSON").setOutputKey("raw");
        OrchestrationContext ctx = new OrchestrationContext();

        executor.execute(node, ctx);

        assertEquals("标题", ctx.get("title"));
        assertEquals(3, ctx.get("score"));
        assertTrue(ctx.contains("raw"));
    }

    @Test
    void 系统提示词存在时改用消息列表() {
        FlowNodeDefinition node = new FlowNodeDefinition()
                .setNodeCode("n").setSystemPrompt("你是#{role}").setPromptTemplate("问题")
                .setOutputKey("o");
        OrchestrationContext ctx = new OrchestrationContext().put("role", "助手");

        executor.execute(node, ctx);

        assertNull(aiService.lastRequest.getPrompt());
        assertEquals(2, aiService.lastRequest.getMessages().size());
        assertEquals("你是助手", aiService.lastRequest.getMessages().get(0).get("content"));
    }
}
