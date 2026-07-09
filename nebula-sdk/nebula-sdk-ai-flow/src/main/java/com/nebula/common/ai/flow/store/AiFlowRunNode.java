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
 * AI流程编排执行节点轨迹表
 * 记录某次执行实例内每个已完成节点的轨迹（执行序 + 完成时产物快照），供排障与执行可视化。
 * JSON 列（output）以字符串存储，经 {@link FlowJsonCodec} 序列化。
 *
 * @author nebula
 */
@Data
@TableName("ai_flow_run_node")
public class AiFlowRunNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属执行实例标识
     */
    private String runId;

    /**
     * 节点编码
     */
    private String nodeCode;

    /**
     * 执行序（本次执行内自增）
     */
    private Integer seq;

    /**
     * 节点状态：SUCCESS
     */
    private String status;

    /**
     * 该节点完成时的产物快照（JSON对象字符串，可空）
     */
    private String output;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
