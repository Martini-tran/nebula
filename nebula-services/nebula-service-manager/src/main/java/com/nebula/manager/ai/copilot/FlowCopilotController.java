package com.nebula.manager.ai.copilot;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.context.UserContext;
import com.nebula.manager.dto.CopilotStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程设计助手（Copilot）控制器（管理员端）。
 * 提供基于 SSE 的「对话式生成流程 / 派生 Agent」端点（{@code /admin/ai-flow/copilot/stream}）：
 * 由 {@link FlowCopilotService} 驱动混合流式工具循环，把过程转发为 SSE 事件流，事件名约定
 * {@code delta}/{@code tool_call}/{@code flow}/{@code agent}/{@code done}/{@code error}。
 *
 * <p>与通用对话 {@code /admin/ai-chat/stream} 区别：本端点是一个固定的、可调工具并直接落库的助手。
 * 权限统一以 {@code manager:ai-copilot:stream} 作为入口闸门；生成/派生走编程式调用 Service。
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

        // R1：在容器请求线程里取用户身份，传进独立线程（ThreadLocal 不跨线程，SSE 线程内需回填）
        Long userId = UserContext.getUserId();
        CopilotSseSink sink = new CopilotSseSink(emitter, terminated);

        streamExecutor.execute(() -> {
            try {
                flowCopilotService.run(request, userId, sink);
            } catch (Throwable e) {
                // 放宽到 Throwable：SSE 线程内任何逸出的 Error/异常都转成 error 事件，
                // 避免被线程池静默吞掉导致响应体为空。
                log.error("Copilot 流式对话执行失败", e);
                sink.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
        return emitter;
    }
}
