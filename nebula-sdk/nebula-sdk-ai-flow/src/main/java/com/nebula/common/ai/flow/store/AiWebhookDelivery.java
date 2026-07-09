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
 * 编排回调投递记录表（日志 + 断点续发）
 * 对应 SDK 的 {@code com.nebula.common.ai.webhook.WebhookDelivery}。{@code payload} 存回调 body JSON，
 * 重发时直接读它、不重跑 Agent。见 docs/编排回调Webhook设计.md 第五章。
 *
 * @author nebula
 */
@Data
@TableName("ai_webhook_delivery")
public class AiWebhookDelivery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 投递唯一标识（业务键，重发幂等键之一） */
    private String deliveryId;

    /** 来源实例 */
    private String instanceId;

    /** 来源迭代链 */
    private String chainId;

    /** 迭代链第几轮 */
    private Integer seq;

    private String agentCode;

    /** 节点级回调才有 */
    private String nodeCode;

    /** ITERATION_ADVANCED | INSTANCE_SUCCESS | INSTANCE_FAILED | NODE_SUCCESS | NODE_FAILED */
    private String event;

    /** 发去哪 */
    private String url;

    /** INLINE(当场发) | DEFER(线程池发) */
    private String mode;

    /** 回调 body JSON（重发直接读它） */
    private String payload;

    /** PENDING | SUCCESS | FAILED | DEAD */
    private String status;

    /** 已尝试次数 */
    private Integer attempts;

    /** 重发上限，达到后置 DEAD */
    private Integer maxAttempts;

    /** 最后一次 HTTP 响应码 */
    private Integer responseCode;

    /** 最后一次失败原因 */
    private String lastError;

    /** 下次重发时间 */
    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
