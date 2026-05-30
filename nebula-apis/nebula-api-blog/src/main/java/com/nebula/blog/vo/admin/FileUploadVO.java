package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件上传结果 VO
 */
@Data
public class FileUploadVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件资产 ID（对应 blog_file_asset.id）
     */
    private Long id;

    /**
     * 访问 URL（OSS 地址）
     */
    private String url;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 文件用途：image / cover / attachment / other
     */
    private String fileType;
}
