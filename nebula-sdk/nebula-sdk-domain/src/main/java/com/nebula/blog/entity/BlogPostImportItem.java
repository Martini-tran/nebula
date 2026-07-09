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
 * 博客文章导入明细表（逐文件）
 *
 * @author nebula
 */
@Data
@TableName("blog_post_import_item")
public class BlogPostImportItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属导入任务ID
     */
    private Long taskId;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 是否导入成功
     */
    private Boolean success;

    /**
     * 成功时的文章ID
     */
    private Long articleId;

    /**
     * 派生标题
     */
    private String title;

    /**
     * 最终 slug
     */
    private String slug;

    /**
     * 失败原因
     */
    private String error;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
