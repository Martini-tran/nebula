package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章批量导入单个文件结果 VO
 */
@Data
public class PostImportResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 是否导入成功
     */
    private boolean success;

    /**
     * 成功时返回的文章 ID
     */
    private Long articleId;

    /**
     * 派生出的标题
     */
    private String title;

    /**
     * 最终使用的 slug
     */
    private String slug;

    /**
     * 失败原因（success=false 时填充）
     */
    private String error;
}
