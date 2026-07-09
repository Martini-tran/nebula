package com.nebula.common.ai.orchestration;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 编排上下文保留键注册表
 * 收编散落在各执行器里的全部 {@code __} 前缀「系统约定键」，按作用域分类集中定义，成为唯一权威出处。
 * 键要能直接当 {@code String} 用于 {@link OrchestrationContext#put}/{@code get} 与模板 {@code {{key}}} 占位，
 * 故用工具类常量而非 enum（enum 会强加 {@code .key()} 取值噪声）。
 *
 * <p><b>保留前缀约定</b>：{@code __} 前缀为系统保留，业务节点的 {@code outputKey}、END 出参字段名不得以其开头，
 * 由 {@code ReservedKeyValidator} 在构图期校验，防业务键与系统键相撞后静默互覆盖。
 *
 * <p>各执行器原有 {@code XXX_KEY} 常量保留为本表常量的别名（{@code = ContextKeys.Xxx.YYY}），外部引用零改动，
 * 只是把字面量收敛到一处、消除重复定义漂移的风险。
 *
 * @author nebula
 */
public final class ContextKeys {

    /**
     * 系统保留键前缀
     */
    public static final String RESERVED_PREFIX = "__";

    private ContextKeys() {
    }

    /**
     * 系统级键：流程/实例身份，贯穿整次编排（由引擎写入，节点只读）
     */
    public static final class System {

        /** 当前流程编码 */
        public static final String FLOW_CODE = "__flowCode";

        /** 当前执行实例 runId */
        public static final String RUN_ID = "__runId";

        /** 当前 Agent 实例 id */
        public static final String CURRENT_INSTANCE_ID = "__currentInstanceId";

        /** 本流程递归子 Agent 深度上限 */
        public static final String MAX_AGENT_DEPTH = "__maxAgentDepth";

        private System() {
        }
    }

    /**
     * 循环键：LOOP 节点每轮写入子上下文，供循环体成员引用
     */
    public static final class Loop {

        /** 当前遍历项（FOREACH） */
        public static final String ITEM = "__loopItem";

        /** 当前轮次索引（0 起） */
        public static final String INDEX = "__loopIndex";

        /** 总轮数 */
        public static final String COUNT = "__loopCount";

        /** 各轮产物收集列表 */
        public static final String RESULTS = "__loopResults";

        /** 最后一轮产物 */
        public static final String LAST_RESULT = "__loopLastResult";

        private Loop() {
        }
    }

    /**
     * Agent 键：递归子 Agent 治理与唤醒
     */
    public static final class Agent {

        /** 递归调用链（List&lt;agentCode&gt;），深度/循环引用治理 */
        public static final String CALL_STACK = "__agentCallStack";

        /** 挂起态被 signal 唤醒时携带的事件名 */
        public static final String SIGNAL_EVENT = "__signalEvent";

        private Agent() {
        }
    }

    /**
     * 状态机键：单点状态推进的终态标记
     */
    public static final class StateMachine {

        /** 失败错误信息 */
        public static final String ERROR = "__smError";

        /** 挂起标记 */
        public static final String SUSPENDED = "__smSuspended";

        private StateMachine() {
        }
    }

    /**
     * 输出键：流程最终结果
     */
    public static final class Output {

        /** END 渲染出的固定 JSON 对象（上层读取流程最终输出） */
        public static final String OUTPUT = "__output";

        private Output() {
        }
    }

    /**
     * 是否为系统保留键（{@code __} 前缀）
     *
     * @param key 键
     * @return 是否保留
     */
    public static boolean isReserved(String key) {
        return key != null && key.startsWith(RESERVED_PREFIX);
    }

    /**
     * 全部已登记的保留键（供诊断/测试，不含运行期动态展开键）
     *
     * @return 保留键集合
     */
    public static Set<String> all() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(System.FLOW_CODE);
        keys.add(System.RUN_ID);
        keys.add(System.CURRENT_INSTANCE_ID);
        keys.add(System.MAX_AGENT_DEPTH);
        keys.add(Loop.ITEM);
        keys.add(Loop.INDEX);
        keys.add(Loop.COUNT);
        keys.add(Loop.RESULTS);
        keys.add(Loop.LAST_RESULT);
        keys.add(Agent.CALL_STACK);
        keys.add(Agent.SIGNAL_EVENT);
        keys.add(StateMachine.ERROR);
        keys.add(StateMachine.SUSPENDED);
        keys.add(Output.OUTPUT);
        return keys;
    }
}
