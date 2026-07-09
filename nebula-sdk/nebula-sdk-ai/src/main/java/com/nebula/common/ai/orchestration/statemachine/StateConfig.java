package com.nebula.common.ai.orchestration.statemachine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 状态配置
 * 一个状态（节点）"失败了怎么办"的强类型视图，源自 {@code ai_flow_node.node_config} 的 {@code stateConfig}
 * 段（决策：状态配置全进 node_config JSON，不新增表列）。承载阶段 1 的三类能力：
 * <ul>
 *   <li><b>重试 Retry</b>：同状态失败后原地再试 N 次（{@link #maxAttempts}/{@link #backoffMs}/{@link #retryOn}）</li>
 *   <li><b>超时 Timeout</b>：执行超过 {@link #timeoutMs} 判失败</li>
 *   <li><b>错误转移 onError</b>：重试耗尽后的去向（{@link OnError}）</li>
 * </ul>
 * 补偿（Saga）相关字段（compensation/checkpoint）阶段 2.5 再加，此处不含。
 *
 * @author nebula
 */
public class StateConfig {

    /**
     * 无重试、失败即置实例 FAILED、不主动挂起的默认配置
     */
    public static final StateConfig NONE =
            new StateConfig(1, 0L, Set.of(), 0L, OnError.FAIL_INSTANCE, null, false, Set.of());

    /**
     * 失败后的处置方式
     */
    public enum OnError {
        /**
         * 实例置 FAILED，落库结束（默认）
         */
        FAIL_INSTANCE,
        /**
         * 转到 {@link StateConfig#errorState} 指定的错误状态（复用转移机制，失败即事件）
         */
        GOTO_STATE,
        /**
         * 挂起等 signal（人工介入，阶段 3 能力；阶段 1 遇到按 FAIL_INSTANCE 兜底并告警）
         */
        SUSPEND
    }

    /**
     * 最大尝试次数（含首次），<1 归一为 1
     */
    private final int maxAttempts;

    /**
     * 重试退避毫秒
     */
    private final long backoffMs;

    /**
     * 命中才重试的错误类型集合（大写）；为空表示任意错误都重试
     */
    private final Set<String> retryOn;

    /**
     * 单次执行超时毫秒，<=0 表示不设超时
     */
    private final long timeoutMs;

    /**
     * 重试耗尽后的处置方式
     */
    private final OnError onError;

    /**
     * onError=GOTO_STATE 时的目标状态编码
     */
    private final String errorState;

    /**
     * 成功执行后是否主动挂起等外部事件（审批/等待型节点的常态，阶段 3）。
     * 与 {@code onError=SUSPEND}（失败挂起）是两条独立的挂起路径。
     */
    private final boolean suspend;

    /**
     * 挂起时等待的事件名集合（{@code signal} 的 event 命中其一即唤醒，阶段 3）；为空表示任意事件都可唤醒
     */
    private final Set<String> awaitingEvents;

    /**
     * 兼容重载：不主动挂起、无等待事件（等价 {@code suspend=false, awaitingEvents=空}）。
     */
    public StateConfig(int maxAttempts, long backoffMs, Set<String> retryOn,
                       long timeoutMs, OnError onError, String errorState) {
        this(maxAttempts, backoffMs, retryOn, timeoutMs, onError, errorState, false, Set.of());
    }

    public StateConfig(int maxAttempts, long backoffMs, Set<String> retryOn,
                       long timeoutMs, OnError onError, String errorState,
                       boolean suspend, Set<String> awaitingEvents) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
        this.retryOn = retryOn == null ? Set.of() : retryOn;
        this.timeoutMs = timeoutMs;
        this.onError = onError == null ? OnError.FAIL_INSTANCE : onError;
        this.errorState = errorState;
        this.suspend = suspend;
        this.awaitingEvents = awaitingEvents == null ? Set.of() : awaitingEvents;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long backoffMs() {
        return backoffMs;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public OnError onError() {
        return onError;
    }

    public String errorState() {
        return errorState;
    }

    /**
     * 成功执行后是否主动挂起等外部事件
     *
     * @return true 表示节点成功后挂起（阶段 3）
     */
    public boolean suspend() {
        return suspend;
    }

    /**
     * 挂起时等待的事件名集合（大写）；为空表示任意事件都可唤醒
     *
     * @return 等待事件集合
     */
    public Set<String> awaitingEvents() {
        return awaitingEvents;
    }

    /**
     * 给定错误类型是否应触发重试：retryOn 为空表示任意错误都重试；否则需命中集合
     *
     * @param errorType 错误类型标识（如 TIMEOUT / TOOL_ERROR），大小写不敏感
     * @return 是否重试
     */
    public boolean shouldRetryOn(String errorType) {
        if (retryOn.isEmpty()) {
            return true;
        }
        return errorType != null && retryOn.contains(errorType.trim().toUpperCase());
    }

    /**
     * 从 {@code node_config.stateConfig} 段（已反序列化为 Map）解析配置。
     * 结构缺失或类型不符时退化为默认值，绝不抛异常（编图期容错，运行时行为可预期）。
     *
     * @param stateConfigSection {@code node_config} 中 {@code stateConfig} 键对应的子 Map，可空
     * @return 解析后的状态配置；入参为空返回 {@link #NONE}
     */
    @SuppressWarnings("unchecked")
    public static StateConfig fromMap(Object stateConfigSection) {
        if (!(stateConfigSection instanceof java.util.Map<?, ?> section) || section.isEmpty()) {
            return NONE;
        }
        int maxAttempts = 1;
        long backoffMs = 0L;
        Set<String> retryOn = new HashSet<>();
        Object retry = section.get("retry");
        if (retry instanceof java.util.Map<?, ?> retryMap) {
            maxAttempts = toInt(retryMap.get("maxAttempts"), 1);
            backoffMs = toLong(retryMap.get("backoffMs"), 0L);
            Object on = retryMap.get("on");
            if (on instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        retryOn.add(String.valueOf(item).trim().toUpperCase());
                    }
                }
            }
        }
        long timeoutMs = toLong(section.get("timeoutMs"), 0L);
        OnError onError = parseOnError(section.get("onError"));
        String errorState = section.get("errorState") == null ? null : String.valueOf(section.get("errorState"));
        boolean suspend = toBool(section.get("suspend"));
        Set<String> awaitingEvents = toEventSet(section.get("awaitingEvents"));
        return new StateConfig(maxAttempts, backoffMs, retryOn, timeoutMs, onError, errorState,
                suspend, awaitingEvents);
    }

    private static boolean toBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        return raw != null && "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    private static Set<String> toEventSet(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Set.of();
        }
        Set<String> events = new HashSet<>();
        for (Object item : list) {
            if (item != null) {
                events.add(String.valueOf(item).trim().toUpperCase());
            }
        }
        return events;
    }

    private static OnError parseOnError(Object raw) {
        if (raw == null) {
            return OnError.FAIL_INSTANCE;
        }
        try {
            return OnError.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OnError.FAIL_INSTANCE;
        }
    }

    private static int toInt(Object raw, int def) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return raw == null ? def : Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long toLong(Object raw, long def) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return raw == null ? def : Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
