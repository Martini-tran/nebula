package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 智能体状态转移历史表（回放/审计）
 * 每次 attempt（含重试失败）落一行：{@code outcome=RETRY} 标记中间失败，恢复时只重放 {@code outcome=SUCCESS} 行。
 * {@code nodeResult} 存该步对 context 的变更集（delta），重放时 apply 它重建 context。
 * 对应 SDK {@code AgentInstanceStore} 的 appendSuccess/appendRetry 落库。
 *
 * @author nebula
 */
@Data
@TableName("ai_agent_instance_transition")
public class AiAgentInstanceTransition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属实例标识
     */
    private String instanceId;

    /**
     * 转移序号，从 0 递增（重试也占序号）
     */
    private Integer seq;

    /**
     * 源状态（可空）
     */
    private String fromState;

    /**
     * 目标状态
     */
    private String toState;

    /**
     * 触发本次转移的事件（如有，阶段 3 挂起唤醒用）
     */
    private String eventName;

    /**
     * 进入 to_state 的重试次数（0=首次）
     */
    private Integer attempt;

    /**
     * 轨迹类型：SUCCESS | RETRY | FAILED | COMPENSATED，回放时可折叠 RETRY 行
     */
    private String outcome;

    /**
     * 预留：1=补偿轨迹（Saga 回滚，阶段 2.5 启用）
     */
    private Integer isCompensation;

    /**
     * 进入 to_state 后该节点产物（delta）；失败/重试时记错误摘要。JSON 字符串，经 {@link FlowJsonCodec} 序列化
     */
    private String nodeResult;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
