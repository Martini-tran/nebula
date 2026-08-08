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
 * AI 流程生成草稿表实体。
 *
 * <p>草稿图以完整 JSON 快照保存，业务层通过 revision 做整图 CAS，避免节点和边处于不同版本。
 *
 * @author nebula
 */
@Data
@TableName("ai_flow_draft")
public class AiFlowDraft implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String draftId;

    private String sessionId;

    private Long userId;

    private String flowCode;

    private String name;

    private String description;

    private String engineType;

    private String graphJson;

    private Long revision;

    private Long lastValidatedRevision;

    private Long lastSimulatedRevision;

    private String status;

    private String committedFlowCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
