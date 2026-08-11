package com.nebula.common.ai.harness.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.flow.CondGroupCompiler;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.flow.ModelProfile;
import com.nebula.common.ai.flow.ModelProfileRepository;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.config.HarnessCommitProperties;
import com.nebula.common.ai.harness.config.HarnessDraftProperties;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.simulate.DagSimulator;
import com.nebula.common.ai.harness.simulate.DraftSimulator;
import com.nebula.common.ai.harness.simulate.PlaceholderSimulationNodeExecutor;
import com.nebula.common.ai.harness.simulate.SimulationConditionEvaluator;
import com.nebula.common.ai.harness.simulate.SimulationExecutorRegistry;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.simulate.SimulationLoopDriver;
import com.nebula.common.ai.harness.simulate.StateMachineSimulator;
import com.nebula.common.ai.harness.simulate.StructuralSimulationNodeExecutor;
import com.nebula.common.ai.harness.validate.CommonRules;
import com.nebula.common.ai.harness.validate.DagRuleSet;
import com.nebula.common.ai.harness.validate.DraftValidator;
import com.nebula.common.ai.harness.validate.StateMachineRuleSet;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「帮我生成一个每天生成博客的 AI Agent 流程」端到端回归。
 *
 * <p>复现用户反馈的真实缺陷形态：模型偷懒时只建了两个结构节点、没有 LLM 节点、
 * 没有提示词、也没有连线，却一路通过校验直到提交。本测试按真实工具链的调用顺序
 * （create_draft → add_node → connect → validate → simulate → commit）逐步驱动
 * {@link DraftApplicationService}，断言**残缺流程必须在提交前被拦住**，
 * 而补齐后的完整流程能够顺利提交。
 *
 * <p>这里直接驱动应用服务而不是走模型：模型输出不可复现，
 * 而缺陷的本质是「服务端没有拦住残缺草稿」，正是本层的职责。
 *
 * @author nebula
 */
class BlogFlowGenerationEndToEndTest {

    private static final DraftAccess ACCESS = new DraftAccess(10L, "session-blog");

    private TestDraftStore store;
    private DraftApplicationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        FlowDefinitionCodec codec = new FlowDefinitionCodec(objectMapper);
        store = new TestDraftStore(codec);
        HarnessDraftProperties properties = new HarnessDraftProperties();

        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("toolRegistry", new ToolRegistry(List.of()));
        Set<String> knownProfiles = Set.of("DS-V3-001");
        beans.addBean("modelProfileRepository", (ModelProfileRepository) code ->
                knownProfiles.contains(code)
                        ? new ModelProfile().setProfileCode(code).setProvider("deepseek").setModel("deepseek-chat")
                        : null);
        List<FlowNodeExecutor> executors = List.of(executor("START"), executor("END"), executor("PROMPT"));
        for (int i = 0; i < executors.size(); i++) {
            beans.addBean("executor" + i, executors.get(i));
        }

        DraftFieldValidator fieldValidator = new DraftFieldValidator(
                beans.getBeanProvider(ToolRegistry.class),
                beans.getBeanProvider(AgentDefinitionRepository.class),
                beans.getBeanProvider(FlowNodeExecutor.class),
                beans.getBeanProvider(ModelProfileRepository.class),
                codec,
                properties);
        ConditionCompiler conditionCompiler = new ConditionCompiler();
        DraftValidator fullValidator = new DraftValidator(
                fieldValidator,
                new CommonRules(),
                List.of(new DagRuleSet(), new StateMachineRuleSet()),
                new FlowGraphFactory(executors, conditionCompiler),
                new FlowStateMachineFactory(executors, conditionCompiler),
                conditionCompiler,
                new CondGroupCompiler(),
                codec);
        HarnessSimulationProperties simulationProperties = new HarnessSimulationProperties();
        SimulationExecutorRegistry simulationRegistry = new SimulationExecutorRegistry(List.of(
                new StructuralSimulationNodeExecutor(), new PlaceholderSimulationNodeExecutor()));
        SimulationConditionEvaluator conditionEvaluator = new SimulationConditionEvaluator(conditionCompiler);
        DraftSimulator simulator = new DraftSimulator(List.of(
                new DagSimulator(simulationProperties, simulationRegistry,
                        new SimulationLoopDriver(simulationProperties, simulationRegistry), conditionEvaluator),
                new StateMachineSimulator(simulationProperties, simulationRegistry, conditionEvaluator)),
                new SimulationInputValidator(objectMapper, simulationProperties));

        service = new DraftApplicationService(
                store, codec, new DraftNodeConverter(objectMapper), fieldValidator, fullValidator, simulator,
                request -> store.markCommitted(request)
                        ? DraftCommitResult.success(request.definition().getFlowCode(), 1)
                        : DraftCommitResult.failure("DRAFT_CONFLICT", "草稿已变化", "重新读取草稿"),
                conditionCompiler, properties, new HarnessCommitProperties(), event -> { });
    }

    /**
     * 缺陷复现：只有 START/END 两个结构节点、无 LLM 节点、无提示词、无连线。
     * 这样的草稿必须无法通过校验，更不可能提交成功。
     */
    @Test
    void 只有两个节点且无连线的残缺流程无法通过校验与提交() {
        String draftId = service.create(ACCESS, "DAG", "daily_blog", "每日博客生成", null).draftId();
        service.addNode(ACCESS, draftId, 0, Map.of("nodeCode", "start", "nodeType", "START"));
        service.addNode(ACCESS, draftId, 1, Map.of("nodeCode", "end", "nodeType", "END"));

        DraftOperationResult validated = service.validate(ACCESS, draftId, 2);

        assertFalse(validated.ok(), "无连线的孤立节点不应通过校验");
        // START 到不了 END：DAG 规则集必须报可达性错误
        assertTrue(validated.issues().stream().anyMatch(issue ->
                        "NODE_UNREACHABLE_FROM_START".equals(issue.code())
                                || "NODE_CANNOT_REACH_END".equals(issue.code())),
                "应报出可达性错误，实际: " + codes(validated));

        // 未模拟且校验有 ERROR，提交必须失败
        assertFalse(service.commit(ACCESS, draftId, 2).ok(), "残缺草稿绝不能提交成功");
        assertEquals(DraftStatus.BUILDING, store.findOwned(draftId, 10L).getStatus());
    }

    /**
     * 缺陷复现（本轮新增拦截）：即使把 START 和 END 连起来让图结构合法，
     * 只有结构节点、没有任何工作节点的「空壳流程」跑完不调模型、不产出内容，必须挡住。
     */
    @Test
    void 连通但没有任何工作节点的空壳流程无法通过校验与提交() {
        String draftId = service.create(ACCESS, "DAG", "daily_blog", "每日博客生成", null).draftId();
        service.addNode(ACCESS, draftId, 0, Map.of("nodeCode", "start", "nodeType", "START"));
        service.addNode(ACCESS, draftId, 1, Map.of("nodeCode", "end", "nodeType", "END"));
        service.connect(ACCESS, draftId, 2, "start", "end", null, null, 0);

        DraftOperationResult validated = service.validate(ACCESS, draftId, 3);

        assertFalse(validated.ok(), "空壳流程不应通过校验");
        assertTrue(validated.issues().stream().anyMatch(issue -> "FLOW_HAS_NO_WORK_NODE".equals(issue.code())),
                "只有结构节点的空壳流程应被拦截，实际: " + codes(validated));
        assertFalse(service.commit(ACCESS, draftId, 3).ok(), "空壳流程绝不能提交成功");
        assertEquals(DraftStatus.BUILDING, store.findOwned(draftId, 10L).getStatus());
    }

    /**
     * 边界：纯工具编排流程（无 LLM 节点）是合法的，不能被空壳检查误伤。
     */
    @Test
    void 纯工具编排流程不会被空壳检查误伤() {
        String draftId = service.create(ACCESS, "DAG", "tool_only", "纯工具流程", null).draftId();
        service.addNode(ACCESS, draftId, 0, Map.of("nodeCode", "start", "nodeType", "START"));
        service.addNode(ACCESS, draftId, 1, Map.of("nodeCode", "call", "nodeType", "TOOL",
                "nodeConfig", Map.of("toolCode", "echo")));

        DraftOperationResult validated = service.validate(ACCESS, draftId, 2);

        assertTrue(validated.issues().stream().noneMatch(issue -> "FLOW_HAS_NO_WORK_NODE".equals(issue.code())),
                "TOOL 节点属于工作节点，不应报空壳，实际: " + codes(validated));
    }

    /**
     * 正向路径：一个真正可用的「每日博客生成」流程。
     * START 声明入参 → 选题 → 写作 → 润色 → END 组装产出，节点带提示词、模型档案与 outputKey。
     */
    @Test
    void 补齐提示词与连线后的完整博客流程可以提交() {
        String draftId = service.create(ACCESS, "DAG", "daily_blog", "每日博客生成", null).draftId();
        long revision = 0;
        revision = next(service.updateMetadata(ACCESS, draftId, revision,
                Map.of("defaultProfileCode", "DS-V3-001"), List.of()));

        revision = next(service.addNode(ACCESS, draftId, revision, Map.of(
                "nodeCode", "start", "nodeType", "START",
                "nodeConfig", Map.of("inputs", Map.of("topic", Map.of("type", "string"))))));
        revision = next(service.addNode(ACCESS, draftId, revision, Map.of(
                "nodeCode", "pick_topic", "nodeType", "PROMPT",
                "systemPrompt", "你是资深技术博客选题编辑。",
                "promptTemplate", "围绕方向 {{topic}} 拟定今天的博客选题，只输出标题。",
                "outputKey", "title", "temperature", 0.9)));
        revision = next(service.addNode(ACCESS, draftId, revision, Map.of(
                "nodeCode", "write", "nodeType", "PROMPT",
                "systemPrompt", "你是技术博客作者，输出 Markdown。",
                "promptTemplate", "以《{{title}}》为题写一篇技术博客正文。",
                "outputKey", "content", "temperature", 0.7, "maxTokens", 4096)));
        revision = next(service.addNode(ACCESS, draftId, revision, Map.of(
                "nodeCode", "polish", "nodeType", "PROMPT",
                "promptTemplate", "润色以下正文，保持 Markdown 结构：\n{{content}}",
                "outputKey", "finalContent", "temperature", 0.3)));
        revision = next(service.addNode(ACCESS, draftId, revision, Map.of(
                "nodeCode", "end", "nodeType", "END",
                "nodeConfig", Map.of("end", Map.of(
                        "outputJson", "{\"title\":\"{{title}}\",\"content\":\"{{finalContent}}\"}")))));

        revision = next(service.connect(ACCESS, draftId, revision, "start", "pick_topic", null, null, 0));
        revision = next(service.connect(ACCESS, draftId, revision, "pick_topic", "write", null, null, 0));
        revision = next(service.connect(ACCESS, draftId, revision, "write", "polish", null, null, 0));
        revision = next(service.connect(ACCESS, draftId, revision, "polish", "end", null, null, 0));

        DraftOperationResult validated = service.validate(ACCESS, draftId, revision);
        assertTrue(validated.ok(), "完整流程应通过校验，实际问题: " + codes(validated));

        DraftOperationResult simulated = service.simulate(ACCESS, draftId, revision, Map.of("topic", "Java 并发"));
        assertTrue(simulated.ok(), "完整流程应通过模拟，实际问题: " + codes(simulated));

        DraftOperationResult committed = service.commit(ACCESS, draftId, revision);
        assertTrue(committed.ok(), "完整流程应提交成功，实际问题: " + codes(committed));

        // 落库产物核对：四个 LLM 节点都带提示词，且上下游变量成链
        FlowDefinition graph = store.findOwned(draftId, 10L).getGraph();
        List<FlowNodeDefinition> llmNodes = graph.getNodes().stream()
                .filter(node -> "PROMPT".equals(node.getNodeType())).toList();
        assertEquals(3, llmNodes.size());
        assertTrue(llmNodes.stream().allMatch(node ->
                node.getPromptTemplate() != null && !node.getPromptTemplate().isBlank()));
        assertTrue(llmNodes.stream().allMatch(node ->
                node.getOutputKey() != null && !node.getOutputKey().isBlank()));
        assertEquals(4, graph.getEdges().size());
    }

    private static long next(DraftOperationResult result) {
        assertTrue(result.ok(), "步骤失败: " + codes(result));
        return result.revision();
    }

    private static String codes(DraftOperationResult result) {
        return result.issues().stream()
                .map(issue -> issue.level() + ":" + issue.code() + "@" + issue.nodeCode())
                .toList().toString();
    }

    private static FlowNodeExecutor executor(String type) {
        return new FlowNodeExecutor() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
                // 校验只需确认执行器存在，不真正执行节点。
            }
        };
    }
}
