package com.nebula.common.ai.agent;

import com.nebula.common.ai.agent.tool.SimpleToolContext;
import com.nebula.common.ai.agent.tool.ToolCallingService;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.memory.AgentMemory;

import java.util.Collection;
import java.util.Map;

/**
 * Agent抽象基类
 * 持有Agent功能编码、记忆门面与AI调用基建，并在发起调用前自动把本Agent的功能编码
 * 注入{@link AiRequest}，使下游的记忆隔离与日志归集都能按Agent区分。
 *
 * @author nebula
 */
public abstract class AbstractAgent implements Agent {

    private final String agentCode;

    private final AgentMemory memory;

    private final AiService aiService;

    /**
     * 工具调用闭环服务，可空。为空时 {@link #chatWithTools} 抛清晰异常提示未启用工具调用。
     */
    private final ToolCallingService toolCallingService;

    protected AbstractAgent(String agentCode, AgentMemory memory, AiService aiService) {
        this(agentCode, memory, aiService, null);
    }

    protected AbstractAgent(String agentCode, AgentMemory memory, AiService aiService,
                            ToolCallingService toolCallingService) {
        if (agentCode == null || agentCode.isEmpty()) {
            throw new IllegalArgumentException("agentCode不能为空");
        }
        this.agentCode = agentCode;
        this.memory = memory;
        this.aiService = aiService;
        this.toolCallingService = toolCallingService;
    }

    @Override
    public String agentCode() {
        return agentCode;
    }

    @Override
    public AgentMemory memory() {
        return memory;
    }

    /**
     * 获取AI服务，供子类按需直接使用
     *
     * @return AI服务
     */
    protected AiService aiService() {
        return aiService;
    }

    /**
     * 发起对话调用
     * 调用前自动将本Agent的功能编码注入请求，再委托{@link AiService#chat(AiRequest)}。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    protected Map<String, Object> chat(AiRequest request) {
        request.setAgentCode(agentCode);
        return aiService.chat(request);
    }

    /**
     * 发起带工具调用闭环的对话（function-calling loop）。
     * 注入本Agent功能编码后，委托 {@link ToolCallingService} 驱动「调模型 → 模型自主选工具 → 执行 → 回灌 → 再调」的循环，
     * 直到模型不再请求工具或到达迭代上限。工具的选择权交给模型，{@code toolCodes} 限定本次可用工具白名单。
     *
     * @param request   请求参数
     * @param toolCodes 本次允许使用的工具编码白名单（对应 {@code ToolDefinition.code()}）
     * @return 模型最终响应
     * @throws IllegalStateException 未装配 {@link ToolCallingService}（工具调用能力未启用）时抛出
     */
    protected Map<String, Object> chatWithTools(AiRequest request, Collection<String> toolCodes) {
        if (toolCallingService == null) {
            throw new IllegalStateException("当前Agent未启用工具调用：请通过四参构造器注入 ToolCallingService，"
                    + "并开启 nebula.ai.tool-calling.enabled=true");
        }
        request.setAgentCode(agentCode);
        SimpleToolContext toolContext = new SimpleToolContext(request.getUserId(), request.getConversationId());
        return toolCallingService.run(request, toolCodes, toolContext);
    }
}
