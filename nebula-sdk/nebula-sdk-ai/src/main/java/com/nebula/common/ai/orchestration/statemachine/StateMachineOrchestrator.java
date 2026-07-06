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
import java.util.Set;
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
 * {@link #run(StateMachineGraph, OrchestrationContext, String, TransitionListener)} 带落库。
 *
 * <p><b>挂起/唤醒（阶段 3）</b>：内部驱动核 {@code drive} 支持在状态处暂停——{@code onError=SUSPEND}（失败挂起）与
 * {@code stateConfig.suspend=true}（成功后主动挂起）两条路径均落 SUSPENDED 并回调 {@code onSuspended} 后返回（不置
 * SUCCESS/FAILED）。{@link #resumeFrom} 从挂起态的出边裁决续跑，供 {@code AgentEngine.signal} 唤醒时调用。
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
        return drive(graph, ctx, instanceId, listener, graph.entryState(), 0, 0);
    }

    /**
     * 从挂起态唤醒后续跑（阶段 3）：从 {@code fromState} 的<b>出边裁决</b>继续推进，不重跑该状态节点。
     * 挂起态节点在挂起前已执行成功（或成功后主动挂起），故 signal 唤醒后只需按 guard + payload 选下一转移。
     *
     * @param graph            从 graph_snapshot 重建的图
     * @param ctx              从 context_snapshot 恢复的上下文（含 signal 写入的 event/payload）
     * @param instanceId       实例标识
     * @param listener         落库监听器
     * @param fromState        挂起时所在状态（续跑起点）
     * @param startSeq         续跑起始 seq（挂起时的 context_snapshot_seq + 1）
     * @param startTransitions 已发生的转移次数（续跑累加，仍受 max_transitions 约束）
     * @return 续跑后的上下文（再次到达终态/失败/挂起）
     */
    public OrchestrationContext resumeFrom(StateMachineGraph graph, OrchestrationContext ctx,
                                           String instanceId, TransitionListener listener,
                                           String fromState, int startSeq, int startTransitions) {
        if (graph == null) {
            throw new OrchestrationException("状态机图不能为空");
        }
        OrchestrationContext context = ctx == null ? new OrchestrationContext() : ctx;
        TransitionListener l = listener == null ? TransitionListener.NOOP : listener;
        StateNode node = graph.state(fromState);
        if (node == null) {
            throw new OrchestrationException("状态机[" + graph.code() + "]续跑起点未知状态: " + fromState);
        }
        // 终态无需续跑；否则从挂起态的出边裁决继续（不重跑该节点）
        if (node.isTerminal()) {
            l.onTerminal(instanceId, fromState, snapshot(context));
            return context;
        }
        int maxTransitions = graph.maxTransitions();
        String next = arbitrate(graph, node, context);
        if (next == null) {
            String msg = "no matching transition at state " + fromState;
            log.warn("状态机[{}] 续跑 {}", graph.code(), msg);
            markFailed(graph, context, msg);
            l.onInstanceFailed(instanceId, fromState, msg, snapshot(context));
            return context;
        }
        int transitionCount = startTransitions + 1;
        if (transitionCount > maxTransitions) {
            return failOnMaxTransitions(graph, context, fromState, maxTransitions, l, instanceId);
        }
        int seq = startSeq;
        log.info("状态机[{}] 从挂起态[{}]唤醒续跑 -> [{}]", graph.code(), fromState, next);
        l.onTransition(instanceId, fromState, next, seq);
        return drive(graph, context, instanceId, l, next, seq, transitionCount);
    }

    /**
     * 从指定状态<b>重新执行该状态节点</b>起步推进（崩溃恢复用，阶段 4）。
     * 与 {@link #resumeFrom}（从挂起态的<b>出边裁决</b>起步，不重跑挂起节点）不同：本方法从 {@code fromState}
     * <b>执行该节点本身</b>起步——用于 RUNNING 断点恢复，此时 currentState 尚未落 SUCCESS（铁律 4 保证"未落账=未干成"），
     * 故重跑该节点是安全的。
     *
     * @param graph            从 graph_snapshot 重建的图
     * @param ctx              增量重放恢复后的上下文
     * @param instanceId       实例标识
     * @param listener         落库监听器
     * @param fromState        续跑起点状态（将重新执行该节点）
     * @param startSeq         续跑起始 seq
     * @param startTransitions 已发生的转移次数
     * @return 续跑后的上下文
     */
    public OrchestrationContext runFrom(StateMachineGraph graph, OrchestrationContext ctx,
                                        String instanceId, TransitionListener listener,
                                        String fromState, int startSeq, int startTransitions) {
        if (graph == null) {
            throw new OrchestrationException("状态机图不能为空");
        }
        return drive(graph, ctx, instanceId, listener, fromState, startSeq, startTransitions);
    }

    /**
     * 内部驱动核（阶段 3 抽出）：从 {@code startState} 起单点推进，直到终态（SUCCESS）/ 走投无路或超上限（FAILED）/
     * 挂起（SUSPENDED）。{@link #run} 从入口态起步，{@link #resumeFrom} 从挂起态的后继起步，共用本循环。
     */
    private OrchestrationContext drive(StateMachineGraph graph, OrchestrationContext ctx,
                                       String instanceId, TransitionListener listener,
                                       String startState, int startSeq, int startTransitions) {
        OrchestrationContext context = ctx == null ? new OrchestrationContext() : ctx;
        TransitionListener l = listener == null ? TransitionListener.NOOP : listener;
        int maxTransitions = graph.maxTransitions();

        String currentState = startState;
        int seq = startSeq;
        int transitionCount = startTransitions;
        log.info("状态机[{}] 从状态[{}]起步，max_transitions={}", graph.code(), currentState, maxTransitions);

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

            // 失败挂起（onError=SUSPEND）：节点失败但配了挂起——不落 SUCCESS，停在原地等 signal
            if (outcome.suspended) {
                return suspend(graph, context, currentState, outcome.awaitingEvents, l, instanceId);
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

            // 成功后主动挂起（stateConfig.suspend=true，审批/等待型节点）：先落 SUCCESS 再挂起等 signal
            if (node.config().suspend()) {
                return suspend(graph, context, currentState, node.config().awaitingEvents(), l, instanceId);
            }

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
     * 挂起收尾：写 {@link #SUSPENDED_KEY} 供上层判定，全量刷 context_snapshot 并回调 {@code onSuspended}。
     * 挂起既非成功也非失败——实例停在 {@code suspendedState}，等 signal 唤醒后从该态续跑。
     */
    private OrchestrationContext suspend(StateMachineGraph graph, OrchestrationContext context,
                                         String suspendedState, Set<String> awaitingEvents,
                                         TransitionListener listener, String instanceId) {
        context.put(SUSPENDED_KEY, suspendedState);
        Set<String> events = awaitingEvents == null ? Set.of() : awaitingEvents;
        log.info("状态机[{}] 状态[{}]挂起，等待事件{}", graph.code(), suspendedState, events);
        listener.onSuspended(instanceId, suspendedState, events, snapshot(context));
        return context;
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
            } catch (StateTimeoutException e) {
                // 超时虽继承 OrchestrationException，但语义是可重试的节点执行失败——走重试/onError 通道（在下面统一处理）
                lastError = e;
                if (isNonRetryable(config, attempt, e)) {
                    break;
                }
                onRetry(listener, instanceId, node, config, seq, attempt, e);
                attempt++;
            } catch (OrchestrationException e) {
                // 编排契约级错误（深度超限/循环引用/子 Agent 缺失/未知状态等）不是节点业务失败——
                // 不重试、不走 onError，直接冒泡让上层感知致命错误
                throw e;
            } catch (RuntimeException e) {
                lastError = e;
                if (isNonRetryable(config, attempt, e)) {
                    break;
                }
                onRetry(listener, instanceId, node, config, seq, attempt, e);
                attempt++;
            }
        }
        // 重试耗尽，按 onError 处置
        return applyOnError(graph, node, config, lastError);
    }

    /**
     * 本次失败是否不可再重试：达上限或错误类型不在 retry.on 中。
     */
    private boolean isNonRetryable(StateConfig config, int attempt, RuntimeException e) {
        String errorType = classifyError(e);
        return !(attempt + 1 < config.maxAttempts() && config.shouldRetryOn(errorType));
    }

    /**
     * 记一次可重试的中间失败：落 RETRY 轨迹并按配置退避。
     */
    private void onRetry(TransitionListener listener, String instanceId, StateNode node,
                         StateConfig config, int seq, int attempt, RuntimeException e) {
        String errorType = classifyError(e);
        log.warn("状态机[{}] 状态[{}]第 {} 次执行失败（{}），将重试: {}",
                node.code(), node.code(), attempt, errorType, e.getMessage());
        listener.onStateRetry(instanceId, node.code(), seq, attempt, errorType + ": " + e.getMessage());
        backoff(config.backoffMs());
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
                // 失败挂起（阶段 3）：不置 FAILED，停在原地等 signal，等待 stateConfig.awaitingEvents 指定的事件
                log.info("状态机[{}] 状态[{}]失败→onError=SUSPEND 挂起等 signal", graph.code(), node.code());
                return StepOutcome.suspend(config.awaitingEvents());
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
     * 上下文保留键：状态机挂起时写入的挂起状态编码，供上层判定"实例已挂起"（阶段 3）
     */
    public static final String SUSPENDED_KEY = "__smSuspended";

    /**
     * 单步执行结果：成功 / 实例失败 / 错误转移 / 失败挂起，四选一。成功时携带 attempt 与本步 context 变更集（供落库）；
     * 失败挂起时携带等待事件集。
     */
    private static final class StepOutcome {
        final boolean instanceFailed;
        final String error;
        final String gotoState;
        final boolean suspended;
        final Set<String> awaitingEvents;
        final int attempt;
        final Map<String, Object> contextDelta;

        private StepOutcome(boolean instanceFailed, String error, String gotoState,
                            boolean suspended, Set<String> awaitingEvents,
                            int attempt, Map<String, Object> contextDelta) {
            this.instanceFailed = instanceFailed;
            this.error = error;
            this.gotoState = gotoState;
            this.suspended = suspended;
            this.awaitingEvents = awaitingEvents;
            this.attempt = attempt;
            this.contextDelta = contextDelta;
        }

        static StepOutcome success(int attempt, Map<String, Object> contextDelta) {
            return new StepOutcome(false, null, null, false, Set.of(), attempt, contextDelta);
        }

        static StepOutcome fail(String error) {
            return new StepOutcome(true, error, null, false, Set.of(), 0, Map.of());
        }

        static StepOutcome gotoState(String target) {
            return new StepOutcome(false, null, target, false, Set.of(), 0, Map.of());
        }

        static StepOutcome suspend(Set<String> awaitingEvents) {
            return new StepOutcome(false, null, null, true,
                    awaitingEvents == null ? Set.of() : awaitingEvents, 0, Map.of());
        }
    }
}
