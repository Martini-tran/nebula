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
 * 人物卡表（只是自己的备忘，不关联系统账号；往来时间线按名字实时汇总，不落库）
 *
 * @author nebula
 */
@Data
@TableName("space_person")
public class SpacePerson implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人物ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 姓名，同一用户下不重复
     */
    private String name;

    /**
     * 平时怎么称呼
     */
    private String alias;

    /**
     * 其他叫法 JSON 数组
     */
    private String extraNames;

    /**
     * 分组
     */
    private String personGroup;

    /**
     * 头像颜色 #rrggbb
     */
    private String color;

    /**
     * 生日 MM-DD；可清空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String birthday;

    /**
     * 多少天没联系就提醒；空为不提醒，可清空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer contactEvery;

    /**
     * 认识于 / 一句话介绍
     */
    private String intro;

    /**
     * 手填的信息 JSON 数组 [{label,value}]
     */
    private String facts;

    /**
     * 备忘
     */
    private String memo;

    /**
     * 手记的联系 JSON 数组 [{date,note}]
     */
    private String contacts;

    /**
     * 手记的承诺 JSON 数组 [{id,who,text,due,done,createTime}]
     */
    private String promises;

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
