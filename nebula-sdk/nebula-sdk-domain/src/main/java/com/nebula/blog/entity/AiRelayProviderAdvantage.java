package com.nebula.blog.entity;

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
 * AI中转服务商优势
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider_advantage")
public class AiRelayProviderAdvantage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 优势ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务商ID（ai_relay_provider.id）
     */
    private Long providerId;

    /**
     * 优势标题
     */
    private String title;

    /**
     * 优势说明
     */
    private String content;

    /**
     * 优势类型（1普通优势 2核心优势 3风险提示）
     */
    private Integer advantageType;

    /**
     * 优势图标文件ID（sys_file）
     */
    private Long iconFileId;

    /**
     * 展示排序
     */
    private Integer sortOrder;

    /**
     * 状态（1正常 0停用）
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
