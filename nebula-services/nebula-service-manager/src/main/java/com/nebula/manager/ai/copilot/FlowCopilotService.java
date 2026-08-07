package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessMessage;
import com.nebula.common.ai.harness.conversation.HarnessRequest;
import com.nebula.common.ai.harness.runtime.FlowGenerationHarness;
import com.nebula.manager.dto.CopilotStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Flow Copilot 的传输适配服务。
 *
 * <p>该服务不直接调用模型，也不维护工具循环、白名单或工具线程池；生成控制面统一由
 * {@link FlowGenerationHarness} 负责。这里仅把 Manager DTO 转为 SDK 请求并接入 SSE 事件投影。
 *
 * @author nebula
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCopilotService {

    private final FlowGenerationHarness flowGenerationHarness;

    /**
     * 运行一次流程生成对话。
     *
     * @param request HTTP 请求 DTO
     * @param context 服务端构造的认证上下文
     * @param sink    SSE 事件投影
     */
    public void run(CopilotStreamRequest request, HarnessCallContext context, CopilotSseSink sink) {
        if (request == null) {
            throw new IllegalArgumentException("Copilot 请求不能为空");
        }
        log.debug("开始 Flow Harness 对话: requestId={}, conversationId={}, userId={}",
                context.requestId(), context.sessionId(), context.userId());
        flowGenerationHarness.run(toHarnessRequest(request), context, sink);
    }

    private HarnessRequest toHarnessRequest(CopilotStreamRequest request) {
        List<HarnessMessage> messages = request.getMessages() == null
                ? List.of()
                : request.getMessages().stream()
                        .filter(message -> message != null)
                        .map(message -> new HarnessMessage(message.getRole(), message.getContent()))
                        .toList();
        return new HarnessRequest(
                request.getPrompt(),
                messages,
                request.getConversationId(),
                request.getModel(),
                request.getTemperature());
    }
}
