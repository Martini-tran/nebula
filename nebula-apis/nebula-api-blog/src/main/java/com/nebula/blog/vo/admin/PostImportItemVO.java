package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章批量导入单文件明细 VO
 */
@Data
public class PostImportItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细ID
     */
    private Long id;

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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
