package com.nebula.common.file.dto;

import lombok.Data;

/**
 * 文件上传请求参数
 * 业务侧上传时携带的元信息，用于和业务实体关联
 *
 * @author nebula
 */
@Data
public class FileUploadRequest {

    /**
     * 关联业务类型（如：blog_post、user_avatar、travel_note）
     */
    private String targetType;

    /**
     * 关联业务实体ID（可选，未确定时可先不传，后续通过 bind 接口绑定）
     */
    private Long targetId;

    /**
     * 文件用途类型（logo、cover、avatar、attachment、image、video、other）
     * 未传时根据 MIME 类型推断
     */
    private String fileType;

    /**
     * 存储桶名称（未传则使用默认桶）
     */
    private String bucket;

    /**
     * 是否公开访问（1公开 0私有），默认 1
     */
    private Integer isPublic;

    /**
     * 排序值，默认 0
     */
    private Integer sortOrder;

    /**
     * 上传路径前缀（可选）
     */
    private String prefix;
}
