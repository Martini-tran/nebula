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
 * 博客系列表
 *
 * @author nebula
 */
@Data
@TableName("blog_series")
public class BlogSeries implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 系列ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 系列名称
     */
    private String name;

    /**
     * 系列URL标识（唯一）
     */
    private String slug;

    /**
     * 系列简介
     */
    private String description;

    /**
     * 封面文件ID（关联blog_file_asset）
     */
    private Long coverFileId;

    /**
     * 状态：draft(草稿)/published(已发布)/archived(已归档)
     */
    private String status;

    /**
     * 可见性：public(公开)/private(私有)
     */
    private String visibility;

    /**
     * 是否完结
     */
    private Boolean isFinished;

    /**
     * 系列列表排序
     */
    private Integer sortOrder;

    /**
     * 创建者ID（关联sys_user）
     */
    private Long createBy;

    /**
     * 产出本系列的迭代链 chainId（webhook 落库用；手工建的系列为空）
     */
    private String chainId;

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
