package com.nebula.manager.ai.copilot;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;
import com.nebula.common.ai.harness.realrun.DraftRealRunService;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.context.UserContext;
import com.nebula.manager.dto.CopilotConfirmationRequest;
import com.nebula.manager.dto.CopilotStreamRequest;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * 流程设计助手（Copilot）控制器（管理员端）。
 * 提供基于 SSE 的「对话式生成流程 / 派生 Agent」端点（{@code /admin/ai-flow/copilot/stream}）：
 * 由 {@link FlowCopilotService} 将请求交给 Flow Harness，并把 Harness 事件投影为 SSE 事件流，事件名约定
 * {@code delta}/{@code tool_call}/{@code draft_updated}/{@code confirm_required}/
 * {@code operation_updated}/{@code flow}/{@code agent}/{@code done}/{@code error}。
 *
 * <p>与通用对话 {@code /admin/ai-chat/stream} 区别：本端点通过 Harness 细粒度修改、校验、模拟并提交流程草稿。
 * 权限统一以 {@code manager:ai-copilot:stream} 作为入口闸门；真实试跑确认使用独立 HTTP 接口和结构化恢复请求。
 *
 * @author nebula
 */
@Slf4j
@RestController
@RequestMapping("/admin/ai-flow/copilot")
@RequiredArgsConstructor
public class FlowCopilotController {

    /**
     * SSE 连接超时（毫秒）。一轮含多次模型调用与工具执行，故较通用对话更长。
     */
    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final FlowCopilotService flowCopilotService;

    private final DraftRealRunService draftRealRunService;

    private final DraftApplicationService draftApplicationService;

    /**
     * 流式对话线程池：SseEmitter 需在独立线程写出，避免阻塞容器请求线程。
     */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ai-copilot-sse");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 发起流程设计助手对话（SSE 流式）。
     *
     * @param request 请求（本轮 prompt + 历史 messages）
     * @return SSE 发射器
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("manager:ai-copilot:stream")
    public SseEmitter stream(@RequestBody CopilotStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean terminated = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            terminated.set(true);
            emitter.complete();
        });
        emitter.onError(throwable -> terminated.set(true));
        emitter.onCompletion(() -> terminated.set(true));

        // 必须在请求线程快照认证上下文；SSE 与 Harness 工具线程不得读取请求 ThreadLocal。
        Long userId = UserContext.getUserId();
        String requestId = UUID.randomUUID().toString();
        HarnessCallContext callContext = new HarnessCallContext(
                userId == null ? null : String.valueOf(userId),
                request == null ? null : request.getConversationId(),
                new LinkedHashSet<>(UserContext.getPermissions()),
                requestId);
        CopilotSseSink sink = new CopilotSseSink(emitter, terminated);

        streamExecutor.execute(() -> {
            try {
                flowCopilotService.run(request, callContext, sink);
            } catch (Throwable e) {
                // 放宽到 Throwable：SSE 线程内任何逸出的 Error/异常都转成 error 事件，
                // 避免被线程池静默吞掉导致响应体为空。
                log.error("Copilot 流式对话执行失败: requestId={}, conversationId={}",
                        requestId, callContext.sessionId(), e);
                sink.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
        return emitter;
    }

    /** 用户显式确认一次真实试跑，只签发授权，不在 HTTP 请求线程执行草稿。 */
    @PostMapping("/confirmations/{confirmationId}/confirm")
    @SaCheckPermission("manager:ai-copilot:stream")
    public R<Map<String, Object>> confirm(@PathVariable String confirmationId,
                                          @RequestBody(required = false) CopilotConfirmationRequest request) {
        String conversationId = request == null ? null : request.getConversationId();
        return R.success(draftRealRunService.confirm(draftAccess(conversationId), confirmationId).toResponse());
    }

    /** 客户端断连或工具有界等待结束后，按 operationId 查询持久化状态。 */
    @GetMapping("/operations/{operationId}")
    @SaCheckPermission("manager:ai-copilot:stream")
    public R<Map<String, Object>> operation(@PathVariable String operationId) {
        return R.success(draftRealRunService.getOperation(draftAccess(null), operationId).toResponse());
    }

    /** 画布按 revision 和边分页读取 canonical 草稿，不依赖模型上下文。 */
    @GetMapping("/drafts/{draftId}")
    @SaCheckPermission("manager:ai-copilot:stream")
    public R<Map<String, Object>> draft(@PathVariable String draftId,
                                       @RequestParam(required = false) String conversationId,
                                       @RequestParam(required = false) Integer edgePage,
                                       @RequestParam(required = false) Integer edgePageSize) {
        return R.success(draftApplicationService.read(
                draftAccess(conversationId), draftId, null, edgePage, edgePageSize).toToolResponse());
    }

    /** 编辑器读取完整 canonical 草稿定义；与面向模型的分页 read_draft 契约隔离。 */
    @GetMapping("/drafts/{draftId}/definition")
    @SaCheckPermission("manager:ai-copilot:stream")
    public R<Map<String, Object>> draftDefinition(@PathVariable String draftId,
                                                 @RequestParam(required = false) String conversationId) {
        return R.success(draftApplicationService.readDefinition(
                draftAccess(conversationId), draftId).toToolResponse());
    }

    /**
     * 应用停止时释放 SSE 调度线程，避免热重启残留守护线程。
     */
    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdown();
    }

    private DraftAccess draftAccess(String conversationId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("缺少已认证用户身份");
        }
        return new DraftAccess(userId, conversationId == null || conversationId.isBlank() ? null : conversationId);
    }
}
