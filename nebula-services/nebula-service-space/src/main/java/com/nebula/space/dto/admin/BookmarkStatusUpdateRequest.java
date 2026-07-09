package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台书签状态更新请求
 */
@Data
public class BookmarkStatusUpdateRequest {

    /**
     * 状态：0正常 1归档 2失效
     */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
