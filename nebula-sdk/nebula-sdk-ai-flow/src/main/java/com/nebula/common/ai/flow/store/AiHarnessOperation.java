package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Harness 高风险动作的幂等操作记录。 */
@Data
@TableName("ai_harness_operation")
public class AiHarnessOperation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String operationId;
    private String action;
    private String draftId;
    private Long draftRevision;
    private Long userId;
    private String sessionId;
    private String inputDigest;
    private String status;
    private String resultSummaryJson;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime heartbeatAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
