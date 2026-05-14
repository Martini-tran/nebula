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
 * 博客内容版本表（完整快照）
 *
 * @author nebula
 */
@Data
@TableName("blog_content_version")
public class BlogContentVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 版本标题
     */
    private String title;

    /**
     * 版本摘要
     */
    private String summary;

    /**
     * 版本正文文件ID
     */
    private Long contentFileId;

    /**
     * 版本封面文件ID
     */
    private Long coverFileId;

    /**
     * 该版本的状态快照
     */
    private String status;

    /**
     * 该版本的可见性快照
     */
    private String visibility;

    /**
     * 变更类型：manual(手动)/auto(自动)
     */
    private String changeType;

    /**
     * 变更备注
     */
    private String changeNote;

    /**
     * 创建者用户ID
     */
    private Long creatorId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}