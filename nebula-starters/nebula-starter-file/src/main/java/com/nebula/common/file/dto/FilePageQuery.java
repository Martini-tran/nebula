package com.nebula.common.file.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FilePageQuery extends PageQuery {

    /**
     * 关联业务类型
     */
    private String targetType;

    /**
     * 关联业务实体ID
     */
    private Long targetId;

    /**
     * 文件用途类型
     */
    private String fileType;

    /**
     * 存储类型
     */
    private String storageType;

    /**
     * 文件状态（1正常 0删除 2上传中 3上传失败 4禁用）
     */
    private Integer status;

    /**
     * 上传人ID
     */
    private Long createBy;

    /**
     * 原始文件名（模糊匹配）
     */
    private String originalFilename;
}
