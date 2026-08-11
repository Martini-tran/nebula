package com.nebula.common.ai.harness.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessRequest;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;
import com.nebula.common.ai.harness.draft.DraftOperationResult;
import com.nebula.common.ai.harness.draft.DraftTestSupport;
import com.nebula.common.ai.harness.tool.HarnessDraftTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「帮我生成一个每天生成博客的 AI Agent 流程」完整工具循环跑通测试。
 *
 * <p>与 {@code BlogFlowGenerationEndToEndTest} 直接调用应用服务不同，本测试走**真实主循环**：
 * {@link FlowGenerationHarness} → {@link HarnessToolScheduler} → 真实草稿工具 → {@link DraftApplicationService}。
 * 模型侧用脚本化假模型（逐轮返回预设 toolCalls）替代真实 LLM——真实模型输出不可复现，
 * 无法作为回归基线；而工具调度、参数校验、revision 递进、事件投影这些**服务端行为**是确定的，
 * 正是本测试要守住的部分。
 *
 * <p>核心断言不是「流程建出来了」，而是**每个节点都有配置、都有数据**：
 * 模型节点有提示词与模型档案、产物节点有 outputKey、上下游用 {{变量}} 成链、
 * END 组装最终产出，并且模拟运行时数据能真正一路传到 END。
 *
 * @author nebula
 */
class BlogAgentFlowLoopTest {

    private static final String CONVERSATION = "conv-blog";
    private static final String USER_ID = "10";

    @Test
    void 模型按工具循环建出的每日博客流程每个节点都有配置和数据() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DraftTestSupport support = DraftTestSupport.create(Set.of("DS-V3-001"));
            DraftApplicationService service = support.service();
            List<ToolDefinition> tools = HarnessDraftTools.all(service);

            ScriptedAiService aiService = new ScriptedAiService(blogFlowScript());
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(tools), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 5000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService, scheduler, List.of(), List.of(), 20);
            RecordingSink sink = new RecordingSink();

            harness.run(
                    new HarnessRequest("帮我生成一个每天生成博客的 AI Agent 流程",
                            List.of(), CONVERSATION, null, null),
                    new HarnessCallContext(USER_ID, CONVERSATION, Set.of("flow:write"), "req-blog"),
                    sink);

            // 1) 工具循环真的跑完了，没有中途失败
            assertTrue(sink.types().contains(HarnessEvent.CONVERSATION_COMPLETED),
                    "工具循环应正常结束，实际事件: " + sink.types());
            assertTrue(aiService.exhausted(), "脚本应被全部消费，剩余轮次: " + aiService.remaining());

            // 2) 草稿确实建出来了
            String draftId = support.singleDraftId();
            assertNotNull(draftId, "应当创建了草稿");
            FlowDefinition graph = support.graphOf(draftId);

            // 3) 结构完整：START + 三个工作节点 + END，五条节点四条边，全部连通
            assertEquals(5, graph.getNodes().size(), "应建出 5 个节点");
            assertEquals(4, graph.getEdges().size(), "应建出 4 条边");

            // 4) 每个模型节点都有提示词、有 outputKey、有可解析的模型档案
            List<FlowNodeDefinition> llmNodes = graph.getNodes().stream()
                    .filter(node -> "PROMPT".equals(node.getNodeType()))
                    .toList();
            assertEquals(3, llmNodes.size(), "应有 3 个 PROMPT 节点");
            for (FlowNodeDefinition node : llmNodes) {
                assertTrue(hasText(node.getPromptTemplate()),
                        node.getNodeCode() + " 缺少 promptTemplate");
                assertTrue(hasText(node.getOutputKey()),
                        node.getNodeCode() + " 缺少 outputKey");
                assertTrue(hasText(node.getProfileCode()) || hasText(graph.getDefaultProfileCode()),
                        node.getNodeCode() + " 没有可用的模型档案");
            }

            // 5) START 声明了入参，END 组装了产出——两端都有数据契约
            FlowNodeDefinition start = support.node(graph, "start");
            assertTrue(start.getNodeConfig().containsKey("inputs"), "START 应声明 inputs");
            FlowNodeDefinition end = support.node(graph, "end");
            assertTrue(String.valueOf(nested(end.getNodeConfig(), "end").get("outputJson")).contains("{{"),
                    "END 应引用上游变量组装产出");

            // 6) 上下游数据成链：每个下游节点引用的变量都来自上游 outputKey 或 START 入参
            assertTrue(support.upstreamVariablesResolved(graph),
                    "存在引用不到上游产物的模板变量");

            // 7) 校验 + 模拟 + 提交全通过，且模拟时数据真的传到了 END
            DraftAccess access = new DraftAccess(Long.valueOf(USER_ID), CONVERSATION);
            long revision = support.revisionOf(draftId);
            DraftOperationResult validated = service.validate(access, draftId, revision);
            assertTrue(validated.ok(), "完整流程应通过校验: " + support.codes(validated));

            DraftOperationResult simulated = service.simulate(access, draftId, revision,
                    Map.of("topic", "Java 并发"));
            assertTrue(simulated.ok(), "完整流程应通过模拟: " + support.codes(simulated));

            DraftOperationResult committed = service.commit(access, draftId, revision);
            assertTrue(committed.ok(), "完整流程应提交成功: " + support.codes(committed));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 反向用例：模型偷懒只建 START/END 两个空壳节点并试图提交，必须被服务端拦下。
     * 这是用户实际遇到的形态，作为回归基线固化。
     */
    @Test
    void 模型只建两个空壳节点时提交会被服务端拒绝() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DraftTestSupport support = DraftTestSupport.create(Set.of("DS-V3-001"));
            DraftApplicationService service = support.service();
            ScriptedAiService aiService = new ScriptedAiService(lazyScript());
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(HarnessDraftTools.all(service)), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 5000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService, scheduler, List.of(), List.of(), 20);
            RecordingSink sink = new RecordingSink();

            harness.run(new HarnessRequest("随便给我建个流程", List.of(), CONVERSATION, null, null),
                    new HarnessCallContext(USER_ID, CONVERSATION, Set.of("flow:write"), "req-lazy"),
                    sink);

            String draftId = support.singleDraftId();
            DraftAccess access = new DraftAccess(Long.valueOf(USER_ID), CONVERSATION);
            long revision = support.revisionOf(draftId);

            DraftOperationResult validated = service.validate(access, draftId, revision);
            assertFalse(validated.ok(), "空壳流程不应通过校验");
            assertTrue(validated.issues().stream()
                            .anyMatch(issue -> "FLOW_HAS_NO_WORK_NODE".equals(issue.code())),
                    "应报空壳流程错误: " + support.codes(validated));
            assertFalse(service.commit(access, draftId, revision).ok(), "空壳流程不能提交");
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------------- 脚本：模拟一个「按纪律办事」的模型 ----------------

    /** 每轮一个 toolCall，顺序对应真实建图过程。 */
    private List<Map<String, Object>> blogFlowScript() {
        List<Map<String, Object>> script = new ArrayList<>();
        script.add(call("c1", "create_draft", Map.of(
                "engineType", "DAG", "flowCode", "daily_blog", "name", "每日博客生成")));
        script.add(call("c2", "update_draft_metadata", Map.of(
                "draftId", "#draft", "expectedRevision", "#rev",
                "patch", Map.of("defaultProfileCode", "DS-V3-001"))));
        script.add(call("c3", "add_node", Map.of(
                "draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "start", "nodeType", "START",
                "nodeConfig", Map.of("inputs", Map.of("topic", Map.of("type", "string"))))));
        script.add(call("c4", "add_node", Map.of(
                "draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "pick_topic", "nodeType", "PROMPT",
                "systemPrompt", "你是资深技术博客选题编辑，只输出标题本身。",
                "promptTemplate", "围绕方向 {{topic}} 拟定今天的博客选题。",
                "outputKey", "title", "temperature", 0.9)));
        script.add(call("c5", "add_node", Map.of(
                "draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "write", "nodeType", "PROMPT",
                "systemPrompt", "你是技术博客作者，输出 Markdown 正文。",
                "promptTemplate", "以《{{title}}》为题写一篇技术博客。",
                "outputKey", "content", "temperature", 0.7, "maxTokens", 4096)));
        script.add(call("c6", "add_node", Map.of(
                "draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "polish", "nodeType", "PROMPT",
                "promptTemplate", "润色以下正文并保持 Markdown 结构：\n{{content}}",
                "outputKey", "finalContent", "temperature", 0.3)));
        script.add(call("c7", "add_node", Map.of(
                "draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "end", "nodeType", "END",
                "nodeConfig", Map.of("end", Map.of(
                        "outputJson", "{\"title\":\"{{title}}\",\"content\":\"{{finalContent}}\"}")))));
        script.add(call("c8", "connect", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "fromNode", "start", "toNode", "pick_topic")));
        script.add(call("c9", "connect", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "fromNode", "pick_topic", "toNode", "write")));
        script.add(call("c10", "connect", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "fromNode", "write", "toNode", "polish")));
        script.add(call("c11", "connect", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "fromNode", "polish", "toNode", "end")));
        return script;
    }

    /** 偷懒脚本：只建两个结构节点并连线，不建任何工作节点。 */
    private List<Map<String, Object>> lazyScript() {
        List<Map<String, Object>> script = new ArrayList<>();
        script.add(call("l1", "create_draft", Map.of(
                "engineType", "DAG", "flowCode", "lazy_flow", "name", "偷懒流程")));
        script.add(call("l2", "add_node", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "start", "nodeType", "START")));
        script.add(call("l3", "add_node", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "nodeCode", "end", "nodeType", "END")));
        script.add(call("l4", "connect", Map.of("draftId", "#draft", "expectedRevision", "#rev",
                "fromNode", "start", "toNode", "end")));
        return script;
    }

    private Map<String, Object> call(String id, String name, Map<String, Object> arguments) {
        return Map.of("id", id, "name", name, "arguments", new LinkedHashMap<>(arguments));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /**
     * 脚本化假模型：每轮吐一个工具调用，脚本耗尽后返回终止文本。
     *
     * <p>参数里的 {@code #draft} / {@code #rev} 占位在发出前替换为上一轮工具返回的真实
     * draftId 与 revision——真实模型正是这样从工具结果里取值继续下一步，
     * 这样才能真正检验 revision CAS 链路，而不是写死。
     */
    private static final class ScriptedAiService implements AiService {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final List<Map<String, Object>> script;
        private final AtomicInteger cursor = new AtomicInteger();
        private String draftId;
        private long revision;

        private ScriptedAiService(List<Map<String, Object>> script) {
            this.script = script;
        }

        void observe(String draftId, Long revision) {
            if (draftId != null) {
                this.draftId = draftId;
            }
            if (revision != null) {
                this.revision = revision;
            }
        }

        boolean exhausted() {
            return cursor.get() >= script.size();
        }

        int remaining() {
            return Math.max(0, script.size() - cursor.get());
        }

        @Override
        public Map<String, Object> chat(AiRequest request) {
            // 从上一轮工具反馈里取出最新 draftId / revision
            harvest(request);
            int index = cursor.getAndIncrement();
            if (index >= script.size()) {
                return Map.of("content", "流程已建好：共 5 个节点、4 条边。");
            }
            Map<String, Object> call = new LinkedHashMap<>(script.get(index));
            // 模型侧的 arguments 是 JSON 字符串，不是 Map——scheduler 会对其做 parseArguments
            call.put("arguments", toJson(resolve(asMap(call.get("arguments")))));
            return Map.of("content", "", "toolCalls", List.of(call));
        }

        /** 扫描消息里的工具反馈 JSON，抽出 draftId / revision。 */
        @SuppressWarnings("unchecked")
        private void harvest(AiRequest request) {
            List<Map<String, Object>> messages = request.getMessages();
            if (messages == null) {
                return;
            }
            for (Map<String, Object> message : messages) {
                Object content = message.get("content");
                if (content == null) {
                    continue;
                }
                String text = String.valueOf(content);
                String parsedDraft = between(text, "\"draftId\":\"", "\"");
                if (parsedDraft != null) {
                    draftId = parsedDraft;
                }
                String parsedRevision = between(text, "\"revision\":", ",");
                if (parsedRevision == null) {
                    parsedRevision = between(text, "\"revision\":", "}");
                }
                if (parsedRevision != null) {
                    try {
                        revision = Long.parseLong(parsedRevision.trim());
                    } catch (NumberFormatException ignored) {
                        // 非数字时保留上一次的值
                    }
                }
            }
        }

        private static String between(String text, String start, String end) {
            int from = text.lastIndexOf(start);
            if (from < 0) {
                return null;
            }
            from += start.length();
            int to = text.indexOf(end, from);
            return to < 0 ? null : text.substring(from, to);
        }

        private Map<String, Object> resolve(Map<String, Object> arguments) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            arguments.forEach((key, value) -> {
                if ("#draft".equals(value)) {
                    resolved.put(key, draftId);
                } else if ("#rev".equals(value)) {
                    resolved.put(key, revision);
                } else {
                    resolved.put(key, value);
                }
            });
            return resolved;
        }

        private static String toJson(Map<String, Object> arguments) {
            try {
                return MAPPER.writeValueAsString(arguments);
            } catch (Exception e) {
                throw new IllegalStateException("脚本参数序列化失败", e);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asMap(Object value) {
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }

        @Override
        public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
            return CompletableFuture.completedFuture(chat(request));
        }

        @Override
        public void stream(AiRequest request, AiCallback callback) {
        }

        @Override
        public void saveMessage(String conversationId, Map<String, Object> message) {
        }

        @Override
        public List<Map<String, Object>> listMessages(String conversationId) {
            return List.of();
        }

        @Override
        public void log(Map<String, Object> record) {
        }

        @Override
        public void clearConversation(String conversationId) {
        }
    }
}
