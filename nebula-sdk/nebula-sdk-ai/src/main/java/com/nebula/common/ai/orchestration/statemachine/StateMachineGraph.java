package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 状态机图
 * 由若干 {@link StateNode} 与 {@link StateTransition} 构成，与 DAG 的 {@code OrchestrationGraph} 并存但
 * <b>允许成环</b>（回跳/循环是状态机的核心能力）。通过 {@link Builder} 构建，构建时执行 Fail-Fast 编图校验：
 * <ul>
 *   <li>边引用必须指向已登记状态；</li>
 *   <li><b>ENTRY 恰好一个</b>——0 个无从起步、&gt;1 个起点歧义，直接拒绝建图；</li>
 *   <li><b>TERMINAL 至少一个</b>——否则永不正常收尾（只能靠 max_transitions 兜底 FAILED）；</li>
 *   <li>可达性：从 ENTRY 不可达的孤立状态仅告警，不拒绝（宽松，容忍编辑中间态）。</li>
 * </ul>
 *
 * <p>与 DAG 图的关键差异：<b>不做无环校验</b>（成环合法）；出边按 {@code sortNo} 升序返回，供内核裁决取首个
 * guard=true 的转移。
 *
 * @author nebula
 */
public class StateMachineGraph {

    private static final Logger log = LoggerFactory.getLogger(StateMachineGraph.class);

    private final String code;

    private final Map<String, StateNode> states;

    private final List<StateTransition> transitions;

    private final String entryState;

    /**
     * 全局转移次数上限（对应 {@code ai_flow.max_transitions}，per-Flow 可配），防死循环
     */
    private final int maxTransitions;

    private StateMachineGraph(String code, Map<String, StateNode> states,
                              List<StateTransition> transitions, String entryState, int maxTransitions) {
        this.code = code;
        this.states = states;
        this.transitions = transitions;
        this.entryState = entryState;
        this.maxTransitions = maxTransitions;
    }

    /**
     * 全局转移次数上限，超过则实例置 FAILED
     *
     * @return 上限（>=1）
     */
    public int maxTransitions() {
        return maxTransitions;
    }

    public String code() {
        return code;
    }

    /**
     * 入口态编码（编图时已校验恰好一个）
     *
     * @return 入口态编码
     */
    public String entryState() {
        return entryState;
    }

    /**
     * 获取状态
     *
     * @param code 状态编码
     * @return 状态，不存在返回 null
     */
    public StateNode state(String code) {
        return states.get(code);
    }

    /**
     * 全部状态编码（保持声明顺序）
     *
     * @return 状态编码集合
     */
    public Set<String> stateCodes() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /**
     * 指定状态的出边，<b>按 sortNo 升序</b>（裁决顺序：取首个 guard=true 的转移）
     *
     * @param stateCode 状态编码
     * @return 出边列表，已按 sortNo 升序
     */
    public List<StateTransition> outTransitions(String stateCode) {
        List<StateTransition> result = new ArrayList<>();
        for (StateTransition t : transitions) {
            if (t.from().equals(stateCode)) {
                result.add(t);
            }
        }
        result.sort(Comparator.comparingInt(StateTransition::sortNo));
        return result;
    }

    /**
     * 创建构建器
     *
     * @param code 流程编码
     * @return 构建器
     */
    public static Builder builder(String code) {
        return new Builder(code);
    }

    /**
     * 状态机图构建器
     */
    public static class Builder {

        private final String code;

        private final Map<String, StateNode> states = new LinkedHashMap<>();

        private final List<StateTransition> transitions = new ArrayList<>();

        private int maxTransitions = 100;

        private Builder(String code) {
            this.code = code;
        }

        /**
         * 设置全局转移次数上限（对应 {@code ai_flow.max_transitions}），<1 归一为默认 100
         *
         * @param maxTransitions 上限
         * @return 当前构建器
         */
        public Builder maxTransitions(int maxTransitions) {
            this.maxTransitions = maxTransitions < 1 ? 100 : maxTransitions;
            return this;
        }

        /**
         * 登记一个状态
         *
         * @param node 状态节点（code 非空、不重复）
         * @return 当前构建器
         */
        public Builder state(StateNode node) {
            if (node == null || node.code() == null || node.code().isEmpty()) {
                throw new OrchestrationException("状态编码不能为空");
            }
            if (states.containsKey(node.code())) {
                throw new OrchestrationException("状态编码重复: " + node.code());
            }
            states.put(node.code(), node);
            return this;
        }

        /**
         * 登记一条转移
         *
         * @param transition 转移
         * @return 当前构建器
         */
        public Builder transition(StateTransition transition) {
            transitions.add(transition);
            return this;
        }

        /**
         * 构建并校验图（Fail-Fast）
         *
         * @return 状态机图
         */
        public StateMachineGraph build() {
            for (StateTransition t : transitions) {
                if (!states.containsKey(t.from())) {
                    throw new OrchestrationException("转移引用了未登记的起点状态: " + t.from());
                }
                if (!states.containsKey(t.to())) {
                    throw new OrchestrationException("转移引用了未登记的终点状态: " + t.to());
                }
            }
            String entry = assertEntryUnique();
            assertHasTerminal();
            warnUnreachable(entry);
            return new StateMachineGraph(code, new LinkedHashMap<>(states),
                    new ArrayList<>(transitions), entry, maxTransitions);
        }

        /**
         * 校验 ENTRY 恰好一个，返回入口态编码
         */
        private String assertEntryUnique() {
            List<String> entries = new ArrayList<>();
            for (StateNode node : states.values()) {
                if (node.isEntry()) {
                    entries.add(node.code());
                }
            }
            if (entries.isEmpty()) {
                throw new OrchestrationException("状态机[" + code + "]缺少入口态：需恰好一个 state_type=ENTRY 的状态");
            }
            if (entries.size() > 1) {
                throw new OrchestrationException("状态机[" + code + "]入口态歧义：state_type=ENTRY 的状态有 "
                        + entries.size() + " 个 " + entries + "，需恰好一个");
            }
            return entries.get(0);
        }

        /**
         * 校验至少一个 TERMINAL
         */
        private void assertHasTerminal() {
            boolean hasTerminal = states.values().stream().anyMatch(StateNode::isTerminal);
            if (!hasTerminal) {
                throw new OrchestrationException("状态机[" + code + "]缺少终态：至少需一个 state_type=TERMINAL 的状态");
            }
        }

        /**
         * 可达性告警：从 ENTRY 出发 BFS，未覆盖的状态告警（不拒绝建图）
         */
        private void warnUnreachable(String entry) {
            Set<String> reachable = new java.util.HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(entry);
            reachable.add(entry);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                for (StateTransition t : transitions) {
                    if (t.from().equals(cur) && reachable.add(t.to())) {
                        queue.add(t.to());
                    }
                }
            }
            for (String stateCode : states.keySet()) {
                if (!reachable.contains(stateCode)) {
                    log.warn("状态机[{}] 状态[{}]从入口态[{}]不可达，将永不执行", code, stateCode, entry);
                }
            }
        }
    }
}
