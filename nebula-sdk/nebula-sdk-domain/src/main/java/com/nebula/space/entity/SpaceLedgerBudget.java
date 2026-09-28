package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 记账月预算表（同一月一行）
 *
 * @author nebula
 */
@Data
@TableName("space_ledger_budget")
public class SpaceLedgerBudget implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 月份 YYYY-MM
     */
    private String budgetMonth;

    /**
     * 总预算（分），空为各分类之和
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long total;

    /**
     * 分类预算 JSON：分类ID → 额度（分）
     */
    private String items;

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
}
