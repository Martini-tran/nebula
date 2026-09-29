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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公开主页设置表（每人一行；访客页由服务端按区块开关实时拼，只返回白名单字段）
 *
 * @author nebula
 */
@Data
@TableName("space_profile")
public class SpaceProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 总开关：0关 1开
     */
    private Integer enabled;

    /**
     * 地址里的短名 /@handle，全站唯一
     */
    private String handle;

    /**
     * 一句话介绍
     */
    private String bio;

    /**
     * 外部链接 JSON 数组 [{label,url}]
     */
    private String links;

    /**
     * Now：手写的近况，一行一条
     */
    private String nowText;

    /**
     * Now 最近一次改动的日期；可清空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nowUpdated;

    /**
     * 区块顺序与开关 JSON 数组 [{key,on}]
     */
    private String blocks;

    /**
     * 公开的书签合集 JSON 数组 [{folderId,title,description}]
     */
    private String collections;

    /**
     * 不公开的已读文章ID JSON 数组
     */
    private String hiddenReading;

    /**
     * 摘录精选的划线ID JSON 数组
     */
    private String quoteIds;

    /**
     * 合集被访客展开的累计次数
     */
    private Integer collectionViews;

    /**
     * 合集被访客导入的累计次数
     */
    private Integer importCount;

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
