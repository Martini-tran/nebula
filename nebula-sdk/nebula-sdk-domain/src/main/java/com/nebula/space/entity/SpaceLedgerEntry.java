package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账流水表
 *
 * @author nebula
 */
@Data
@TableName("space_ledger_entry")
public class SpaceLedgerEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 金额（分），正数
     */
    private Long amount;

    /**
     * 收支：out / in
     */
    private String direction;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 日期
     */
    private LocalDate entryDate;

    /**
     * 备注
     */
    private String note;

    /**
     * 由哪个周期账单自动生成，手记的为空
     */
    private Long recurringId;

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
