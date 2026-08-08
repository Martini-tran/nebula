package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Harness 高风险动作的服务端确认记录。 */
@Data
@TableName("ai_harness_confirmation")
public class AiHarnessConfirmation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String confirmationId;
    private String action;
    private String draftId;
    private Long draftRevision;
    private Long userId;
    private String sessionId;
    private String inputDigest;
    private String tokenHash;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
