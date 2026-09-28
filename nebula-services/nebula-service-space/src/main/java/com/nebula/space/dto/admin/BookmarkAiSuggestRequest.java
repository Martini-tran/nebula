package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 书签 AI 整理建议请求：一次一小批，前端分批提交
 */
@Data
public class BookmarkAiSuggestRequest {

    /**
     * 待整理的书签ID列表
     */
    @NotEmpty(message = "书签ID列表不能为空")
    @Size(max = 30, message = "一次最多整理30条书签")
    private List<Long> bookmarkIds;

    /**
     * 要做的事：folder 归目录 / tags 打标签 / title 改标题 / description 补描述
     */
    @NotEmpty(message = "请至少选择一项整理内容")
    private List<String> actions;
}
