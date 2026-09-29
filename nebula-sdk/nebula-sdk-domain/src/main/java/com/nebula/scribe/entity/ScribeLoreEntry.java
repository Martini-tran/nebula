package com.nebula.scribe.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 写作台设定条目表
 *
 * <p>人物、地点、势力、道具、世界观规则，挂在作品下。归属校验走所属作品，本表不冗余 user_id。</p>
 *
 * @author nebula
 */
@Data
@TableName("scribe_lore_entry")
public class ScribeLoreEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设定条目ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属作品ID
     */
    private Long workId;

    /**
     * 类型：character/location/faction/item/rule
     */
    private String kind;

    /**
     * 名称，同一作品内不重复
     */
    private String name;

    /**
     * 别名数组，JSON 文本
     */
    private String aliases;

    /**
     * 一句话概述
     */
    private String summary;

    /**
     * 详细设定，Markdown
     */
    private String detail;

    /**
     * 标签数组，JSON 文本
     */
    private String tags;

    /**
     * AI 生成时是否默认带上
     */
    private Boolean pinned;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0否 1是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 删除时间
     */
    private LocalDateTime deleteTime;
}
