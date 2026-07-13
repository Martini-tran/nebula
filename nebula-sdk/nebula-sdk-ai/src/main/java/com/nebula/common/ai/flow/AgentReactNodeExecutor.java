package com.nebula.common.ai.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.tool.SimpleToolContext;
import com.nebula.common.ai.agent.tool.ToolCallingService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.util.AiTemplateUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct 里程碑执行节点执行器（{@code AGENT_REACT} 类型）
 *
 * <p>把已就绪的工具调用闭环 {@link ToolCallingService}（function-calling loop）接成状态机中的一个节点：
 * 用编排上下文渲染提示词、按「节点覆盖 &gt; 节点档案 &gt; 全局配置」解析模型参数，然后交由
 * {@link ToolCallingService#run} 驱动「调模型 → 模型自主选工具 → 执行 → 回灌 → 再调」的循环，
 * 直到模型不再请求工具或到达迭代上限，最后把终轮文本写回上下文供下游/后续转移引用。
 *
 * <p><b>与既有执行器的分工</b>：
 * <ul>
 *   <li>{@link PromptNodeExecutor}：纯文本生成，不选工具；</li>
 *   <li>{@link ToolNodeExecutor}：固定调用 {@code nodeConfig.toolCode} 指定的<b>单个</b>工具；</li>
 *   <li>本执行器：给模型一批 {@code nodeConfig.toolCodes} 白名单，<b>由模型自主在其中多轮选调不同工具</b>
 *       （ReAct）。适用于"计划-执行"里"里程碑内自主调不同工具完成"的执行节点。</li>
 * </ul>
 *
 * <p><b>模型参数解析</b>与 {@link PromptNodeExecutor} 完全一致（同一套 provider/model/temperature… 覆盖链），
 * 差别仅在最终调用从 {@code aiService.chat} 换为 {@code toolCallingService.run}。
 *
 * @author nebula
 */
@Slf4j
public class AgentReactNodeExecutor implements FlowNodeExecutor {

    /**
     * 节点类型标识
     */
    public static final String TYPE = "AGENT_REACT";

    /**
     * {@code nodeConfig} 中工具白名单的键：值为工具编码字符串数组（对应 {@code ToolDefinition.code()}）。
     * 为空时退化为普通对话（模型无工具可选）。
     */
    public static final String CONFIG_TOOL_CODES = "toolCodes";

    private final ToolCallingService toolCallingService;

    private final ModelProfileRepository profileRepository;

    private final ObjectMapper objectMapper;

    public AgentReactNodeExecutor(ToolCallingService toolCallingService,
                                  ModelProfileRepository profileRepository,
                                  ObjectMapper objectMapper) {
        this.toolCallingService = toolCallingService;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        Map<String, Object> variables = resolveVariables(node, ctx);
        AiRequest request = buildRequest(node, ctx, variables);
        List<String> toolCodes = resolveToolCodes(node);

        SimpleToolContext toolContext = new SimpleToolContext(ctx.getUserId(), ctx.getConversationId());
        Map<String, Object> response = toolCallingService.run(request, toolCodes, toolContext);

        String content = response == null ? "" : String.valueOf(response.getOrDefault("content", ""));
        if (response != null && Boolean.TRUE.equals(response.get("truncated"))) {
            log.warn("ReAct节点[{}] 工具调用闭环到达迭代上限即截断，返回最后一轮响应（可能未完成里程碑）",
                    node.getNodeCode());
        }
        writeOutput(node, ctx, content);
    }

    /**
     * 读工具白名单 {@code nodeConfig.toolCodes}（字符串数组）。为空/缺省则返回空列表——
     * {@link ToolCallingService#run} 遇空白名单退化为普通对话，不报错。
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveToolCodes(FlowNodeDefinition node) {
        Map<String, Object> config = node.getNodeConfig();
        Object raw = config == null ? null : config.get(CONFIG_TOOL_CODES);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (Object item : (List<Object>) list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                codes.add(String.valueOf(item));
            }
        }
        return codes;
    }

    /**
     * 构建模板变量池：以上下文全部产物为基础，再按输入映射补充/重命名（模板变量名 -&gt; 上下文键）。
     * 与 {@link PromptNodeExecutor#resolveVariables} 保持一致。
     */
    private Map<String, Object> resolveVariables(FlowNodeDefinition node, OrchestrationContext ctx) {
        Map<String, Object> variables = new LinkedHashMap<>(ctx.attributes());
        Map<String, String> mapping = node.getInputMapping();
        if (mapping != null && !mapping.isEmpty()) {
            mapping.forEach((templateVar, ctxKey) -> {
                if (templateVar != null && ctxKey != null) {
                    variables.put(templateVar, ctx.get(ctxKey));
                }
            });
        }
        return variables;
    }

    /**
     * 组装 AI 请求：渲染提示词、按「节点覆盖 &gt; 节点档案 &gt; 全局配置」合并模型参数。
     * 与 {@link PromptNodeExecutor#buildRequest} 保持一致（工具调用闭环每轮复用同一 request 的模型参数）。
     */
    private AiRequest buildRequest(FlowNodeDefinition node, OrchestrationContext ctx, Map<String, Object> variables) {
        ModelProfile profile = node.getProfileCode() == null ? null : profileRepository.findByCode(node.getProfileCode());

        AiRequest request = new AiRequest();
        request.setAgentCode(ctx.getString(FlowEngine.FLOW_CODE_KEY));
        request.setConversationId(ctx.getConversationId());
        request.setUserId(ctx.getUserId());

        request.setProvider(firstNonBlank(node.getProvider(), profile == null ? null : profile.getProvider()));
        request.setModel(firstNonBlank(node.getModel(), profile == null ? null : profile.getModel()));
        request.setBaseUrl(firstNonBlank(node.getBaseUrl(), profile == null ? null : profile.getBaseUrl()));
        request.setApiKey(firstNonBlank(node.getApiKey(), profile == null ? null : profile.getApiKey()));
        request.setTemperature(firstNonNull(node.getTemperature(), profile == null ? null : profile.getTemperature()));
        request.setMaxTokens(firstNonNull(node.getMaxTokens(), profile == null ? null : profile.getMaxTokens()));
        request.setTopP(firstNonNull(node.getTopP(), profile == null ? null : profile.getTopP()));
        request.setTimeoutMs(firstNonNull(node.getTimeoutMs(), profile == null ? null : profile.getTimeoutMs()));
        if (node.getStop() != null && !node.getStop().isEmpty()) {
            request.setStop(new ArrayList<>(node.getStop()));
        }
        request.setOptions(mergeOptions(profile, node));
        request.setVariables(variables);

        String userPrompt = AiTemplateUtils.render(node.getPromptTemplate(), variables);
        String systemPrompt = AiTemplateUtils.render(node.getSystemPrompt(), variables);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(message("system", systemPrompt));
            messages.add(message("user", userPrompt == null ? "" : userPrompt));
            request.setMessages(messages);
        } else {
            request.setPrompt(userPrompt);
        }
        return request;
    }

    /**
     * 合并扩展参数：档案为底，节点逐键覆盖。与 {@link PromptNodeExecutor#mergeOptions} 一致。
     */
    private Map<String, Object> mergeOptions(ModelProfile profile, FlowNodeDefinition node) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (profile != null && profile.getOptions() != null) {
            merged.putAll(profile.getOptions());
        }
        if (node.getOptions() != null) {
            merged.putAll(node.getOptions());
        }
        return merged;
    }

    /**
     * 写回产物：outputKey 缺省用节点编码。JSON 模式解析后逐键展开并保留原文，TEXT 整段写入。
     * 与 {@link PromptNodeExecutor#writeOutput} 一致，使"里程碑产物"能被后续转移的 guard/下游节点引用。
     */
    private void writeOutput(FlowNodeDefinition node, OrchestrationContext ctx, String content) {
        String key = (node.getOutputKey() == null || node.getOutputKey().isBlank())
                ? node.getNodeCode() : node.getOutputKey();
        if ("JSON".equalsIgnoreCase(node.getOutputMode())) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(content,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
                parsed.forEach(ctx::put);
            } catch (Exception e) {
                log.warn("ReAct节点[{}] JSON产物解析失败，退化为整段写入: {}", node.getNodeCode(), e.getMessage());
            }
        }
        ctx.put(key, content);
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
