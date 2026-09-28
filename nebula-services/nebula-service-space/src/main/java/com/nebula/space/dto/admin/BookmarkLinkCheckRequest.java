package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 书签链接检查请求：一次一小批，前端分批提交并显示进度
 */
@Data
public class BookmarkLinkCheckRequest {

    /**
     * 待检查的书签ID列表
     */
    @NotEmpty(message = "书签ID列表不能为空")
    @Size(max = 20, message = "一次最多检查20条书签")
    private List<Long> bookmarkIds;
}
