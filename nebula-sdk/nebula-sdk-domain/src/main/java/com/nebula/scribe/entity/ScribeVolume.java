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
 * 写作台卷表
 *
 * <p>卷是可选的一层：作品没有卷时章节平铺；一旦有卷，所有章节都必须属于某一卷（服务层维护）。
 * 归属校验走所属作品，本表不冗余 user_id。</p>
 *
 * @author nebula
 */
@Data
@TableName("scribe_volume")
public class ScribeVolume implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 卷ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属作品ID
     */
    private Long workId;

    /**
     * 卷名
     */
    private String title;

    /**
     * 本卷梗概
     */
    private String synopsis;

    /**
     * 排序值，按 1000 间隔稀疏分配
     */
    private Integer sortOrder;

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
