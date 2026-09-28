package com.nebula.space.service;

import com.nebula.space.dto.admin.BookmarkAiSuggestRequest;
import com.nebula.space.dto.admin.FolderAiPlanRequest;
import com.nebula.space.vo.admin.BookmarkAiSuggestionVO;
import com.nebula.space.vo.admin.FolderAiPlanVO;

import java.util.List;

/**
 * 书签 AI 整理：只出建议，不改数据。用户在前端逐项勾选后，用现有的书签 / 目录 / 标签接口应用
 */
public interface SpaceBookmarkAiService {

    /**
     * 给一批书签出整理建议：归到哪个目录、加哪些标签、标题怎么改、描述怎么补
     *
     * @return 有改动的书签的建议，顺序同入参
     */
    List<BookmarkAiSuggestionVO> suggest(BookmarkAiSuggestRequest req);

    /**
     * 给整棵目录树出重排方案：新建、改名、移动、合并
     */
    FolderAiPlanVO plan(FolderAiPlanRequest req);
}
