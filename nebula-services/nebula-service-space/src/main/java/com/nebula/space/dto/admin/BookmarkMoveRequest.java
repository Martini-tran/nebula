package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 后台书签批量移动请求
 */
@Data
public class BookmarkMoveRequest {

    /**
     * 待移动的书签ID列表
     */
    @NotNull(message = "书签ID列表不能为空")
    private List<Long> bookmarkIds;

    /**
     * 目标目录ID，0表示未分类
     */
    @NotNull(message = "目标目录ID不能为空")
    private Long targetFolderId;
}
