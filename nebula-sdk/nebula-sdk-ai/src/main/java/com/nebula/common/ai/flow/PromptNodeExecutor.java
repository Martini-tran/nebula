package com.nebula.common.ai.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.util.AiTemplateUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词节点执行器
 * 处理 {@code PROMPT} 类型节点：用编排上下文中的变量渲染提示词模板，按「节点覆盖 &gt; 节点档案 &gt;
 * 全局配置」的优先级解析模型调用参数，调用 {@link AiService}，并把模型产物写回上下文供下游节点引用。
 *
 * <p>流程默认档案由 {@link FlowGraphFactory} 在构图时规范化继承到节点；流程编码由 {@link FlowEngine}
 * 以保留键写入上下文，用作调用的 {@code agentCode}。
 *
 * @author nebula
 */
@Slf4j
public class PromptNodeExecutor implements FlowNodeExecutor {

    /**
     * 节点类型标识
     */
    public static final String TYPE = "PROMPT";

    private static final String OUTPUT_MODE_JSON = "JSON";

    private final AiService aiService;

    private final ModelProfileRepository profileRepository;

    private final ObjectMapper objectMapper;

    public PromptNodeExecutor(AiService aiService, ModelProfileRepository profileRepository, ObjectMapper objectMapper) {
        this.aiService = aiService;
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
        Map<String, Object> response = aiService.chat(request);
        String content = response == null ? "" : String.valueOf(response.getOrDefault("content", ""));
        writeOutput(node, ctx, content);
    }

    /**
     * 构建模板变量池：以上下文全部产物为基础，再按输入映射补充/重命名（模板变量名 -&gt; 上下文键）。
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
     * 组装AI请求：渲染提示词、按优先级合并模型参数。空参数留 null，由运行时回退到全局配置。
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
     * 合并扩展参数：档案为底，节点逐键覆盖。
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
     * 写回产物：outputKey 缺省用节点编码。TEXT 整段写入；JSON 解析后逐键展开，并保留原文。
     */
    private void writeOutput(FlowNodeDefinition node, OrchestrationContext ctx, String content) {
        String key = (node.getOutputKey() == null || node.getOutputKey().isBlank())
                ? node.getNodeCode() : node.getOutputKey();
        if (OUTPUT_MODE_JSON.equalsIgnoreCase(node.getOutputMode())) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
                });
                parsed.forEach(ctx::put);
            } catch (Exception e) {
                log.warn("节点[{}] JSON产物解析失败，退化为整段写入: {}", node.getNodeCode(), e.getMessage());
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
