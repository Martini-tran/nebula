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
 * AI中转服务商
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider")
public class AiRelayProvider implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务商ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务商名称（如 OpenRouter）
     */
    private String name;

    /**
     * 官网地址
     */
    private String websiteUrl;

    /**
     * Logo文件ID（sys_file）
     */
    private Long logoFileId;

    /**
     * 服务商简介
     */
    private String description;

    /**
     * 综合推荐分（核心排序依据）
     */
    private BigDecimal recommendScore;

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
