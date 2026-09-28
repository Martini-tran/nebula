package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 * 记账周期账单表（到日子自动记一笔）
 *
 * @author nebula
 */
@Data
@TableName("space_ledger_recurring")
public class SpaceLedgerRecurring implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 周期账单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 名称，生成的流水用它当备注
     */
    private String note;

    /**
     * 金额（分）
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
     * 每月几号（1-28）
     */
    private Integer dayOfMonth;

    /**
     * 启用：0暂停 1启用
     */
    private Integer active;

    /**
     * 从哪个月开始 YYYY-MM
     */
    private String startMonth;

    /**
     * 已生成到哪个月 YYYY-MM
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String filledThrough;

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
