package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 后台书签批量删除请求
 */
@Data
public class BookmarkBatchDeleteRequest {

    /**
     * 待删除的书签ID列表
     */
    @NotEmpty(message = "书签ID列表不能为空")
    private List<Long> bookmarkIds;
}
