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
 * 博客文件资源表
 *
 * @author nebula
 */
@Data
@TableName("blog_file_asset")
public class BlogFileAsset implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 存储类型：local(本地)/oss(对象存储)
     */
    private String storageType;

    /**
     * OSS bucket名称（本地存储时可为空）
     */
    private String bucket;

    /**
     * 存储路径，如 posts/2026/04/xxx.md
     */
    private String objectKey;

    /**
     * 访问URL
     */
    private String url;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 文件扩展名（如 .md, .jpg）
     */
    private String extension;

    /**
     * MIME类型（如 text/markdown, image/jpeg）
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;

    /**
     * 文件SHA256哈希值（用于去重/校验）
     */
    private String hashSha256;

    /**
     * 文件用途：markdown/image/attachment/cover/other
     */
    private String fileType;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}