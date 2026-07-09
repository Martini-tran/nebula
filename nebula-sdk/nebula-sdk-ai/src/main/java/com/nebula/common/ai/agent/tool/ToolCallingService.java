package com.nebula.common.ai.agent.tool;

import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.ToolContext;

import java.util.Collection;
import java.util.Map;

/**
 * 工具调用闭环服务（function-calling loop）
 * 驱动「调模型 → 模型决定调哪个工具 → 执行工具 → 把结果回灌 → 再调模型」的循环，直到模型不再请求工具或到达迭代上限。
 * 让 Agent 真正「会用工具」：工具的选择权交给模型，本服务只负责把工具声明下发、把模型的工具调用落地执行并回灌。
 *
 * <p>每轮模型调用复用 {@code AiService.chat}，因此完整经过过滤器链、调用日志与事件；工具的发现与执行复用
 * {@code ToolRegistry} 中的 {@link com.nebula.common.ai.flow.ToolDefinition}，与 flow 编排共享同一套工具实现。
 *
 * @author nebula
 */
public interface ToolCallingService {

    /**
     * 运行一次工具调用闭环。
     *
     * @param request           AI 请求（messages 会在循环中被追加 assistant/tool 消息）
     * @param allowedToolCodes  本次允许模型使用的工具编码白名单；为空或未启用工具调用时退化为普通对话
     * @param toolContext       工具运行上下文（携带用户/会话身份，供工具读写共享态）
     * @return 模型最终响应（终止轮）。到达迭代上限仍未终止时返回最后一次响应并含 {@code truncated=true}
     */
    Map<String, Object> run(AiRequest request, Collection<String> allowedToolCodes, ToolContext toolContext);
}
