package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台目录创建请求
 */
@Data
public class FolderCreateRequest {

    /**
     * 父目录ID，0表示根目录
     */
    private Long parentId;

    /**
     * 目录名称
     */
    @NotBlank(message = "目录名称不能为空")
    @Size(max = 100, message = "目录名称长度不能超过100")
    private String name;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 来源：manual/chrome/import
     */
    private String source;

    /**
     * 外部来源标识
     */
    @Size(max = 255)
    private String sourceKey;

    /**
     * 备注
     */
    @Size(max = 500)
    private String remark;
}
