package com.nebula.space.vo.me;

import com.nebula.space.vo.admin.BookmarkAdminVO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 「今天」页一次取齐的数据：前端再按当天口径分组，这里只保证不漏
 */
@Data
public class TodayVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当天到期的、过期没做完的、本周（weekStart 起）完成的
     */
    private List<TaskVO> tasks = List.of();

    /**
     * 当天的会议
     */
    private List<MeetingVO> meetings = List.of();

    /**
     * 当天写的笔记，加上最近改过的几条（未归档），最近改过的在前
     */
    private List<NoteVO> notes = List.of();

    /**
     * 当天收藏的书签；没有书签列表权限时为 null，前端不显示这一块
     */
    private List<BookmarkAdminVO> bookmarks;
}
