package com.nebula.system.entity;

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
 * 统一文件资源表
 *
 * @author nebula
 */
@Data
@TableName("sys_file")
public class SysFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联业务类型（如：blog_post、user_avatar、travel_note）
     */
    private String targetType;

    /**
     * 关联业务实体ID
     */
    private Long targetId;

    /**
     * 文件用途类型（logo、cover、avatar、attachment、image、video、other）
     */
    private String fileType;

    /**
     * 存储类型（local、minio、oss、cos）
     */
    private String storageType;

    /**
     * 存储平台标识（minio-prod、aliyun-oss、tencent-cos等）
     */
    private String storagePlatform;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 对象存储Key/文件路径
     */
    private String objectKey;

    /**
     * 文件访问URL
     */
    private String url;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 存储文件名（重命名后的文件名）
     */
    private String storedFilename;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;

    /**
     * 图片宽度（像素）
     */
    private Integer width;

    /**
     * 图片高度（像素）
     */
    private Integer height;

    /**
     * 音视频时长（秒）
     */
    private Integer duration;

    /**
     * 文件SHA256哈希值，用于去重和秒传
     */
    private String hashSha256;

    /**
     * 是否公开（1公开 0私有）
     */
    private Integer isPublic;

    /**
     * 文件状态（1正常 0删除 2上传中 3上传失败 4禁用）
     */
    private Integer status;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 扩展元数据（JSON格式）
     */
    private String metadata;

    /**
     * 上传人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

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
