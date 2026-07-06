package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 状态机编排器（阶段 1）
 * 单点状态推进内核，与 {@code DagOrchestrator} 并存。从入口态起步，循环执行"执行当前状态 → 裁决出边 → 转移"，
 * 直到到达终态（实例 SUCCESS）、裁决无路可走或超过转移上限（实例 FAILED）。支持回跳/成环（靠 guard 出环）、
 * 节点重试/超时/错误转移（读 {@link StateConfig}）。
 *
 * <p><b>落库（阶段 2）</b>：通过 {@link TransitionListener} 把每步转移上报给 {@code AgentInstanceStore} 落
 * {@code ai_agent_instance} 两表。{@link #run(StateMachineGraph, OrchestrationContext)} 保留纯内存重载（listener=NOOP），
 * {@link #run(StateMachineGraph, OrchestrationContext, String, TransitionListener)} 带落库。{@code onError=SUSPEND}
 * 仍降级为 FAIL_INSTANCE 并告警（挂起是阶段 3 能力）。
 *
 * <p><b>控制流契约</b>：严格"<b>先干活后记账</b>"——先 apply 节点产物到 context，再触发 listener 记轨迹。
 * 保证"transition 有 SUCCESS 记录 ⇒ 该步一定已干成"，崩溃恢复可安全重放 SUCCESS 行。接入 listener 后
 * 控制流分支一行未改，仅在既有记账点旁触发回调。
 *
 * <p><b>转移裁决</b>（文档 5.3）：状态成功后，按出边 {@code sortNo} 升序逐条求值 guard，取<b>首个为 true</b> 的转移
 * （短路，default 边须放最后）；零匹配且当前非终态 → 实例 FAILED；终态无出边，到达即 SUCCESS。
 *
 * @author nebula
 */
public class StateMachineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StateMachineOrchestrator.class);

    /**
     * 用于超时熔断的执行线程池；null 表示不启用超时能力（所有 timeoutMs 视为无效）。
     */
    private final ExecutorService timeoutExecutor;

    public StateMachineOrchestrator() {
        this(null);
    }

    public StateMachineOrchestrator(ExecutorService timeoutExecutor) {
        this.timeoutExecutor = timeoutExecutor;
    }

    /**
     * 按状态机图执行（纯内存，不落库）
     *
     * @param graph 状态机图
     * @param ctx   编排共享上下文（产物就地写入并返回）
     * @return 执行后的上下文
     */
    public OrchestrationContext run(StateMachineGraph graph, OrchestrationContext ctx) {
        return run(graph, ctx, null, TransitionListener.NOOP);
    }

    /**
     * 按状态机图执行，并通过 {@link TransitionListener} 上报每步转移（落库/审计）。
     *
     * @param graph      状态机图
     * @param ctx        编排共享上下文（产物就地写入并返回）
     * @param instanceId 实例标识（透传给 listener；纯内存执行传 null）
     * @param listener   转移监听器（null 视为 {@link TransitionListener#NOOP}）
     * @return 执行后的上下文
     */
    public OrchestrationContext run(StateMachineGraph graph, OrchestrationContext ctx,
                                    String instanceId, TransitionListener listener) {
        if (graph == null) {
            throw new OrchestrationException("状态机图不能为空");
        }
        OrchestrationContext context = ctx == null ? new OrchestrationContext() : ctx;
        TransitionListener l = listener == null ? TransitionListener.NOOP : listener;
        int maxTransitions = graph.maxTransitions();

        String currentState = graph.entryState();
        int seq = 0;
        int transitionCount = 0;
        log.info("状态机[{}] 从入口态[{}]起步，max_transitions={}", graph.code(), currentState, maxTransitions);

        while (true) {
            StateNode node = graph.state(currentState);
            if (node == null) {
                throw new OrchestrationException("状态机[" + graph.code() + "]到达未知状态: " + currentState);
            }

            // 执行当前状态（带重试/超时）。失败按 onError 决定去向或终止。先干活后记账：
            // executeState 内部已 apply 产物并对重试中间失败回调 onStateRetry。
            StepOutcome outcome = executeState(graph, node, context, instanceId, seq, l);
            if (outcome.instanceFailed) {
                markFailed(graph, context, outcome.error);
                l.onInstanceFailed(instanceId, currentState, outcome.error, snapshot(context));
                return context;
            }

            if (outcome.gotoState != null) {
                // 错误转移：节点失败但配了 onError=GOTO_STATE，把"失败"当事件跳到 errorState（不落 SUCCESS）
                if (++transitionCount > maxTransitions) {
                    return failOnMaxTransitions(graph, context, currentState, maxTransitions, l, instanceId);
                }
                log.info("状态机[{}] 状态[{}]失败→错误转移到[{}]", graph.code(), currentState, outcome.gotoState);
                seq++;
                l.onTransition(instanceId, currentState, outcome.gotoState, seq);
                currentState = outcome.gotoState;
                continue;
            }

            // 真正执行成功：落一行 SUCCESS transition（记账，严格在 apply 产物之后）
            l.onStateSucceeded(instanceId, currentState, seq, outcome.attempt, outcome.contextDelta);

            // 执行成功。终态：到达即 SUCCESS，收尾。
            if (node.isTerminal()) {
                log.info("状态机[{}] 到达终态[{}]，实例 SUCCESS", graph.code(), currentState);
                l.onTerminal(instanceId, currentState, snapshot(context));
                return context;
            }

            // 裁决下一转移
            String next = arbitrate(graph, node, context);
            if (next == null) {
                // 非终态却无匹配转移 → 走投无路
                String msg = "no matching transition at state " + currentState;
                log.warn("状态机[{}] {}", graph.code(), msg);
                markFailed(graph, context, msg);
                l.onInstanceFailed(instanceId, currentState, msg, snapshot(context));
                return context;
            }
            if (++transitionCount > maxTransitions) {
                return failOnMaxTransitions(graph, context, currentState, maxTransitions, l, instanceId);
            }
            log.info("状态机[{}] 转移 [{}] -> [{}]（第 {} 次）", graph.code(), currentState, next, transitionCount);
            seq++;
            l.onTransition(instanceId, currentState, next, seq);
            currentState = next;
        }
    }

    /**
     * 全量产物快照（拷贝一份，避免 listener 持有可变引用）
     */
    private Map<String, Object> snapshot(OrchestrationContext context) {
        return new LinkedHashMap<>(context.attributes());
    }

    /**
     * 执行单个状态：重试循环 + 超时熔断 + 错误处置（对齐文档 4·5.3 伪逻辑）。
     * 成功即 apply 产物到 context（先干活）后返回成功，并携带本步 context 变更集（delta）供落库；
     * 重试中间失败经 {@code listener.onStateRetry} 落 RETRY 轨迹；重试耗尽按 onError 返回 FAIL/GOTO。
     */
    private StepOutcome executeState(StateMachineGraph graph, StateNode node, OrchestrationContext context,
                                     String instanceId, int seq, TransitionListener listener) {
        StateConfig config = node.config();
        int attempt = 0;
        RuntimeException lastError;
        while (true) {
            // 执行前快照，用于事后求本步写入的 delta（node_result = 该步对 context 的变更集）
            Map<String, Object> before = new HashMap<>(context.attributes());
            try {
                // 干活：执行节点行为（产物由行为直接写回 context —— 先干活）
                runWithOptionalTimeout(node, context, config.timeoutMs());
                if (attempt > 0) {
                    log.info("状态机[{}] 状态[{}]第 {} 次尝试成功", graph.code(), node.code(), attempt);
                }
                return StepOutcome.success(attempt, computeDelta(before, context.attributes()));
            } catch (RuntimeException e) {
                lastError = e;
                String errorType = classifyError(e);
                boolean canRetry = attempt + 1 < config.maxAttempts() && config.shouldRetryOn(errorType);
                log.warn("状态机[{}] 状态[{}]第 {} 次执行失败（{}）: {}",
                        graph.code(), node.code(), attempt, errorType, e.getMessage());
                // 记账：这一次尝试失败落一行 RETRY（若还会重试）或作为失败前最后一次尝试
                if (canRetry) {
                    listener.onStateRetry(instanceId, node.code(), seq, attempt,
                            errorType + ": " + e.getMessage());
                    attempt++;
                    backoff(config.backoffMs());
                } else {
                    break;
                }
            }
        }
        // 重试耗尽，按 onError 处置
        return applyOnError(graph, node, config, lastError);
    }

    /**
     * 求本步对 context 的变更集：after 中新增或值变化的键。node_result 存这份 delta，重放时 apply 它重建 context。
     */
    private Map<String, Object> computeDelta(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> delta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            if (!Objects.equals(before.get(e.getKey()), e.getValue())) {
                delta.put(e.getKey(), e.getValue());
            }
        }
        return delta;
    }

    /**
     * 重试耗尽后的处置：FAIL_INSTANCE / GOTO_STATE / SUSPEND（阶段 1 降级为 FAIL）。
     */
    private StepOutcome applyOnError(StateMachineGraph graph, StateNode node, StateConfig config, RuntimeException error) {
        switch (config.onError()) {
            case GOTO_STATE:
                String target = config.errorState();
                if (target == null || target.isBlank() || graph.state(target) == null) {
                    String msg = "状态[" + node.code() + "]配置 onError=GOTO_STATE 但 errorState 无效: " + target;
                    log.error("状态机[{}] {}", graph.code(), msg);
                    return StepOutcome.fail(msg);
                }
                return StepOutcome.gotoState(target);
            case SUSPEND:
                // 阶段 1 不支持挂起，降级为失败并告警（挂起是阶段 3 能力）
                log.warn("状态机[{}] 状态[{}]配置 onError=SUSPEND，但挂起是阶段 3 能力，阶段 1 降级为 FAIL_INSTANCE",
                        graph.code(), node.code());
                return StepOutcome.fail("onError=SUSPEND 尚未支持（阶段 3），状态[" + node.code() + "]失败: "
                        + (error == null ? "" : error.getMessage()));
            case FAIL_INSTANCE:
            default:
                return StepOutcome.fail("状态[" + node.code() + "]执行失败: "
                        + (error == null ? "" : error.getMessage()));
        }
    }

    /**
     * 转移裁决：按 sortNo 升序取首个 guard=true 的出边（短路）。无匹配返回 null。
     */
    private String arbitrate(StateMachineGraph graph, StateNode node, OrchestrationContext context) {
        List<StateTransition> outs = graph.outTransitions(node.code());
        for (StateTransition t : outs) {
            if (t.isSatisfied(context)) {
                return t.to();
            }
        }
        return null;
    }

    /**
     * 带可选超时执行节点行为。timeoutMs<=0 或无线程池时直接同步执行。
     * 超时抛 {@link StateTimeoutException}（errorType=TIMEOUT，可被 retry.on 命中）。
     */
    private void runWithOptionalTimeout(StateNode node, OrchestrationContext context, long timeoutMs) {
        if (timeoutMs <= 0 || timeoutExecutor == null) {
            node.behavior().execute(context);
            return;
        }
        Callable<Void> task = () -> {
            node.behavior().execute(context);
            return null;
        };
        Future<Void> future = timeoutExecutor.submit(task);
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new StateTimeoutException("状态[" + node.code() + "]执行超时（" + timeoutMs + "ms）");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new OrchestrationException("状态[" + node.code() + "]执行异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrchestrationException("状态[" + node.code() + "]执行被中断", e);
        }
    }

    /**
     * 错误分类：超时归 TIMEOUT，工具异常归 TOOL_ERROR，其余归 UNKNOWN。
     * 用于匹配 {@code retry.on}，命名与文档 4·5.2 示例（TIMEOUT/TOOL_ERROR）一致。
     */
    private String classifyError(RuntimeException e) {
        if (e instanceof StateTimeoutException) {
            return "TIMEOUT";
        }
        String name = e.getClass().getSimpleName().toUpperCase();
        if (name.contains("TOOL")) {
            return "TOOL_ERROR";
        }
        return "UNKNOWN";
    }

    private void backoff(long backoffMs) {
        if (backoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private OrchestrationContext failOnMaxTransitions(StateMachineGraph graph, OrchestrationContext context,
                                                      String currentState, int max,
                                                      TransitionListener listener, String instanceId) {
        String msg = "exceeded max_transitions（" + max + "）";
        log.warn("状态机[{}] {}，实例置 FAILED", graph.code(), msg);
        markFailed(graph, context, msg);
        listener.onInstanceFailed(instanceId, currentState, msg, snapshot(context));
        return context;
    }

    private void markFailed(StateMachineGraph graph, OrchestrationContext context, String error) {
        // 阶段 1 无 store，失败信息仅记日志 + 写入 context 供上层读取；阶段 2 接入 store 后落库
        context.put(FAILED_ERROR_KEY, error);
        log.error("状态机[{}] 实例 FAILED: {}", graph.code(), error);
    }

    /**
     * 上下文保留键：状态机失败时写入的错误摘要，供上层（如 REST/测试）读取
     */
    public static final String FAILED_ERROR_KEY = "__smError";

    /**
     * 单步执行结果：成功 / 实例失败 / 错误转移到某状态，三选一。成功时携带 attempt 与本步 context 变更集（供落库）。
     */
    private static final class StepOutcome {
        final boolean instanceFailed;
        final String error;
        final String gotoState;
        final int attempt;
        final Map<String, Object> contextDelta;

        private StepOutcome(boolean instanceFailed, String error, String gotoState,
                            int attempt, Map<String, Object> contextDelta) {
            this.instanceFailed = instanceFailed;
            this.error = error;
            this.gotoState = gotoState;
            this.attempt = attempt;
            this.contextDelta = contextDelta;
        }

        static StepOutcome success(int attempt, Map<String, Object> contextDelta) {
            return new StepOutcome(false, null, null, attempt, contextDelta);
        }

        static StepOutcome fail(String error) {
            return new StepOutcome(true, error, null, 0, Map.of());
        }

        static StepOutcome gotoState(String target) {
            return new StepOutcome(false, null, target, 0, Map.of());
        }
    }
}
