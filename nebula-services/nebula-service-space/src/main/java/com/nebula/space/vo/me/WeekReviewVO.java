package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 周回顾一次取齐的数据：这周与上一周（算对比）的会议、专注、打卡，外加任务、笔记与上周的周报
 */
@Data
public class WeekReviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所有没做完的（算逾期、没做完的、对照上周计划），加上从上一周开始以来完成的
     */
    private List<TaskVO> tasks = List.of();

    private List<TaskListVO> lists = List.of();

    private List<MeetingVO> meetings = List.of();

    private List<FocusSessionVO> sessions = List.of();

    /**
     * 含已归档的习惯（归档前的打卡照样算）
     */
    private List<HabitVO> habits = List.of();

    private List<HabitLogVO> logs = List.of();

    /**
     * 这一周里写的笔记，含已归档的
     */
    private List<NoteVO> notes = List.of();

    /**
     * 上一周保存的周报，没写过为 null
     */
    private ReportVO lastReport;
}
