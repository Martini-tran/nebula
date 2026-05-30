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
 * AI模型配置
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_model")
public class AiRelayModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模型编码（如 gpt-4o-mini / claude-3-5-sonnet）
     */
    private String code;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 模型厂商（OpenAI/Anthropic/Google等）
     */
    private String modelVendor;

    /**
     * 模型类型（1文本 2图像 3音频 4多模态 5Embedding）
     */
    private Integer modelType;

    /**
     * 模型说明
     */
    private String description;

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
