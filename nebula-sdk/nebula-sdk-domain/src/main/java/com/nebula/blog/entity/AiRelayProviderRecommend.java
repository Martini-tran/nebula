package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商个人推荐及测评（推荐均为本人实际使用并充值）
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider_recommend")
public class AiRelayProviderRecommend implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 推荐ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务商ID（ai_relay_provider.id）
     */
    private Long providerId;

    /**
     * 推荐原因（精简一句话/摘要，用于列表展示）
     */
    private String recommendReason;

    /**
     * 完整测评内容（支持Markdown）
     */
    private String reviewContent;

    /**
     * 个人测评评分（0-10分）
     */
    private BigDecimal reviewScore;

    /**
     * 优点（多个用换行/分号分隔）
     */
    private String pros;

    /**
     * 缺点（多个用换行/分号分隔）
     */
    private String cons;

    /**
     * 推荐使用场景
     */
    private String useScenario;

    /**
     * 首次使用时间
     */
    private LocalDateTime firstUseTime;

    /**
     * 测评时间
     */
    private LocalDateTime reviewTime;

    /**
     * 推荐时间
     */
    private LocalDateTime recommendTime;

    /**
     * 展示排序
     */
    private Integer sortOrder;

    /**
     * 状态（1正常 0下线）
     */
    private Integer status;

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
