package com.nebula.common.ai.orchestration.statemachine;

/**
 * 状态机语义类型
 * 标注一个状态（节点）在状态机中的角色：{@link #ENTRY} 是唯一起步态，{@link #TERMINAL} 是正常收尾态
 * （到达即实例 SUCCESS），其余为 {@link #NORMAL}。对应 {@code ai_flow_node.state_type} 列，仅
 * {@code engine_type=STATE_MACHINE} 的流程使用；DAG 内核忽略。
 *
 * @author nebula
 */
public enum StateType {

    /**
     * 入口态：状态机的起步状态，编图时必须恰好一个
     */
    ENTRY,

    /**
     * 普通态：既非入口也非终态的中间状态
     */
    NORMAL,

    /**
     * 终态：到达即实例正常结束（SUCCESS），无出边
     */
    TERMINAL;

    /**
     * 解析字符串为状态类型，空或未知按 {@link #NORMAL} 处理（宽松，避免旧数据缺列即报错）
     *
     * @param raw 原始值（大小写不敏感）
     * @return 对应类型，无法识别时返回 {@link #NORMAL}
     */
    public static StateType of(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
