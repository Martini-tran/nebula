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
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试脚手架：装配一套与生产等价的草稿服务栈，并提供断言辅助。
 *
 * <p>供跨包的端到端测试复用，避免每个测试重复百来行 Bean 装配。
 *
 * @author nebula
 */
public final class DraftTestSupport {

    private static final Pattern VARIABLE =
            Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}|#\\{\\s*([\\w.-]+)\\s*}");

    private final DraftApplicationService service;
    private final TestDraftStore store;

    private DraftTestSupport(DraftApplicationService service, TestDraftStore store) {
        this.service = service;
        this.store = store;
    }

    /**
     * 创建一套完整服务栈。
     *
     * @param knownProfiles 视为存在的模型档案编码
     * @return 脚手架
     */
    public static DraftTestSupport create(Set<String> knownProfiles) {
        ObjectMapper objectMapper = new ObjectMapper();
        FlowDefinitionCodec codec = new FlowDefinitionCodec(objectMapper);
        TestDraftStore store = new TestDraftStore(codec);
        HarnessDraftProperties properties = new HarnessDraftProperties();

        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("toolRegistry", new ToolRegistry(List.of()));
        beans.addBean("modelProfileRepository", (ModelProfileRepository) code ->
                knownProfiles.contains(code)
                        ? new ModelProfile().setProfileCode(code)
                                .setProvider("deepseek").setModel("deepseek-chat")
                        : null);
        List<FlowNodeExecutor> executors = List.of(
                executor("START"), executor("END"), executor("PROMPT"),
                executor("TOOL"), executor("IF"), executor("JOIN"));
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
        SimulationConditionEvaluator conditionEvaluator =
                new SimulationConditionEvaluator(conditionCompiler);
        DraftSimulator simulator = new DraftSimulator(List.of(
                new DagSimulator(simulationProperties, simulationRegistry,
                        new SimulationLoopDriver(simulationProperties, simulationRegistry),
                        conditionEvaluator),
                new StateMachineSimulator(simulationProperties, simulationRegistry, conditionEvaluator)),
                new SimulationInputValidator(objectMapper, simulationProperties));

        DraftApplicationService service = new DraftApplicationService(
                store, codec, new DraftNodeConverter(objectMapper), fieldValidator, fullValidator,
                simulator,
                request -> store.markCommitted(request)
                        ? DraftCommitResult.success(request.definition().getFlowCode(), 1)
                        : DraftCommitResult.failure("DRAFT_CONFLICT", "草稿已变化", "重新读取草稿"),
                conditionCompiler, properties, new HarnessCommitProperties(), event -> { });
        return new DraftTestSupport(service, store);
    }

    public DraftApplicationService service() {
        return service;
    }

    /** 当前唯一草稿的 ID；没有或多于一个时返回 null。 */
    public String singleDraftId() {
        List<String> ids = store.draftIds();
        return ids.size() == 1 ? ids.get(0) : null;
    }

    public long revisionOf(String draftId) {
        return store.peek(draftId).getRevision();
    }

    public FlowDefinition graphOf(String draftId) {
        return store.peek(draftId).getGraph();
    }

    public FlowNodeDefinition node(FlowDefinition graph, String nodeCode) {
        return graph.getNodes().stream()
                .filter(node -> nodeCode.equals(node.getNodeCode()))
                .findFirst().orElseThrow(() -> new AssertionError("节点不存在: " + nodeCode));
    }

    /**
     * 校验「每个模板变量都能在上游找到产出」：
     * 变量要么来自 START 的 inputs，要么来自任一节点的 outputKey（含 nodeCode 兜底）。
     */
    public boolean upstreamVariablesResolved(FlowDefinition graph) {
        Set<String> available = new HashSet<>();
        for (FlowNodeDefinition node : graph.getNodes()) {
            if ("START".equalsIgnoreCase(node.getNodeType()) && node.getNodeConfig() != null) {
                Object inputs = node.getNodeConfig().get("inputs");
                if (inputs instanceof Map<?, ?> map) {
                    map.keySet().forEach(key -> available.add(String.valueOf(key)));
                }
            }
            String outputKey = node.getOutputKey();
            available.add(outputKey == null || outputKey.isBlank() ? node.getNodeCode() : outputKey);
        }
        for (FlowNodeDefinition node : graph.getNodes()) {
            for (String variable : variablesOf(node.getPromptTemplate())) {
                if (!available.contains(variable)) {
                    return false;
                }
            }
            for (String variable : variablesOf(node.getSystemPrompt())) {
                if (!available.contains(variable)) {
                    return false;
                }
            }
        }
        return true;
    }

    public String codes(DraftOperationResult result) {
        return result.issues().stream()
                .map(issue -> issue.level() + ":" + issue.code() + "@" + issue.nodeCode())
                .toList().toString();
    }

    private Set<String> variablesOf(String template) {
        Set<String> found = new HashSet<>();
        if (template == null || template.isBlank()) {
            return found;
        }
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) {
            found.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return found;
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
