package com.nebula.space.vo.me;

import com.nebula.space.vo.admin.BookmarkAdminVO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 全局搜索召回的候选：各模块与列表接口同样的结构，前端按同一套规则精确过滤、打分、排序
 *
 * <p>某个模块不参与这次搜索（被 b: 这类前缀排除、或者条件对它没有意义）时是空数组。</p>
 */
@Data
public class SearchResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<TaskVO> tasks = List.of();

    /**
     * 全部任务清单：前端显示清单名与颜色
     */
    private List<TaskListVO> lists = List.of();

    private List<NoteVO> notes = List.of();

    private List<MeetingVO> meetings = List.of();

    private List<ReportVO> reports = List.of();

    /**
     * 文章：正文命中时 content 只带命中的段落；划线所属的文章也在这里（不带正文）
     */
    private List<ReadingItemVO> reading = List.of();

    private List<HighlightVO> highlights = List.of();

    /**
     * 人物：&#64;某人 时连同这个人的卡一起给，前端要用他的其他叫法去认任务和会议里的写法
     */
    private List<PersonVO> people = List.of();

    private List<BookmarkAdminVO> bookmarks = List.of();
}
