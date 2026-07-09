package com.nebula.space.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台目录更新请求
 */
@Data
public class FolderUpdateRequest {

    /**
     * 目录名称
     */
    @Size(max = 100, message = "目录名称长度不能超过100")
    private String name;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    @Size(max = 500)
    private String remark;
}
