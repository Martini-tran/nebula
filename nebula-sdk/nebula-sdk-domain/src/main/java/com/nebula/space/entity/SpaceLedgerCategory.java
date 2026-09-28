package com.nebula.space.entity;

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
 * 记账分类表（第一次读取时按默认分类初始化）
 *
 * @author nebula
 */
@Data
@TableName("space_ledger_category")
public class SpaceLedgerCategory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 名称
     */
    private String name;

    /**
     * 图标：Iconify 名称（集合:名字）
     */
    private String icon;

    /**
     * 颜色 #rrggbb
     */
    private String color;

    /**
     * 收支：out / in
     */
    private String kind;

    /**
     * 自动归类关键词 JSON 数组
     */
    private String keywords;

    /**
     * 排序
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
}
