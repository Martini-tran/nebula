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
 * AI 智能体执行实例表（状态机）
 * 承载状态机实例的自包含运行态：版本锁定的 {@code graphSnapshot}、当前状态、分级落盘的 {@code contextSnapshot}，
 * 支撑回放 / 续跑。对应 SDK 的 {@code com.nebula.common.ai.agent.AgentInstanceSnapshot}。
 * JSON 列（graphSnapshot/inputs/contextSnapshot/outputs/awaitingEvents）以字符串存储，经 {@link FlowJsonCodec} 序列化。
 *
 * <p><b>阶段边界</b>：{@code awaitingEvents}/{@code parentInstanceId}/{@code parentNodeCode}（挂起/递归，阶段 3）与
 * {@code lastCheckpointState}（补偿，阶段 2.5）建列但运行时不写。
 *
 * @author nebula
 */
@Data
@TableName("ai_agent_instance")
public class AiAgentInstance implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 实例唯一标识，业务键，续跑/唤醒入口
     */
    private String instanceId;

    /**
     * 归属 Agent 编码（= 记忆隔离键）
     */
    private String agentCode;

    /**
     * 审计标记：从哪个 Agent 版本创建
     */
    private Integer agentVersion;

    /**
     * 审计标记：引用的编排图编码
     */
    private String flowCode;

    /**
     * 审计标记：从哪个 Flow 版本创建（运行时不据此查表，改用 graphSnapshot）
     */
    private Integer flowVersion;

    /**
     * ★版本锁定核心：创建时编译的完整源图定义 JSON（节点+边+stateConfig+guard）。续跑/回放只从此加载
     */
    private String graphSnapshot;

    /**
     * 归属用户ID
     */
    private String userId;

    /**
     * 关联会话ID
     */
    private String conversationId;

    /**
     * 状态：RUNNING | SUSPENDED | SUCCESS | FAILED
     */
    private String status;

    /**
     * 状态机当前状态（节点编码）
     */
    private String currentState;

    /**
     * 最近越过的检查点状态（补偿回滚边界，阶段 2.5 启用）
     */
    private String lastCheckpointState;

    /**
     * SUSPENDED 时等待的事件名集合（JSON数组，阶段 3 启用）
     */
    private String awaitingEvents;

    /**
     * 初始输入快照（JSON对象字符串）
     */
    private String inputs;

    /**
     * 分区上下文快照（JSON对象字符串），分级落盘：仅挂起/越检查点/终态时全量刷新
     */
    private String contextSnapshot;

    /**
     * 快照对应的 transition seq，恢复时从此 seq 之后重放增量；-1 表示还没全量快照
     */
    private Integer contextSnapshotSeq;

    /**
     * Outputs 快照（JSON对象字符串）
     */
    private String outputs;

    /**
     * 已转移次数，配合 max_transitions 防死循环
     */
    private Integer transitionCount;

    /**
     * 父实例（递归子 Agent 用，顶层为空，阶段 3 用）
     */
    private String parentInstanceId;

    /**
     * 父实例中触发本子实例的 AgentNode 编码（回调唤醒用，阶段 3）
     */
    private String parentNodeCode;

    /**
     * 乐观锁：signal/resume 用 CAS 抢占，防并发重复推进
     */
    private Integer lockVersion;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
