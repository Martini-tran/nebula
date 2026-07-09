package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台目录移动请求
 */
@Data
public class FolderMoveRequest {

    /**
     * 目标父目录ID，0表示根目录
     */
    @NotNull(message = "目标父目录ID不能为空")
    private Long targetParentId;

    /**
     * 移动后的排序
     */
    private Integer sortOrder;
}
