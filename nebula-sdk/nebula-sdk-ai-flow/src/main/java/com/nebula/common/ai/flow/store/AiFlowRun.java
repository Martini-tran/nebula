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
 * AI流程编排执行实例表
 * 一次编排执行对应一条记录，承载断点续跑所需的全部状态：已执行节点集、全量产物快照、状态与失败信息。
 * 对应 SDK 的 {@code com.nebula.common.ai.orchestration.RunSnapshot}。
 * JSON 列（input/attributes/executedNodes）以字符串存储，经 {@link FlowJsonCodec} 序列化。
 *
 * @author nebula
 */
@Data
@TableName("ai_flow_run")
public class AiFlowRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 执行实例唯一标识，业务键，续跑入口
     */
    private String runId;

    /**
     * 流程编码
     */
    private String flowCode;

    /**
     * 流程版本
     */
    private Integer version;

    /**
     * 归属用户ID
     */
    private String userId;

    /**
     * 关联会话ID
     */
    private String conversationId;

    /**
     * 状态：RUNNING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 初始输入快照（JSON对象字符串）
     */
    private String input;

    /**
     * 全量产物快照（JSON对象字符串），每节点完成后刷新
     */
    private String attributes;

    /**
     * 已执行完成的节点编码集合（JSON数组字符串）
     */
    private String executedNodes;

    /**
     * 失败节点编码
     */
    private String failedNode;

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
