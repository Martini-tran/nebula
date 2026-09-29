package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 日历一个时间范围里的数据：日历不是新数据，只是把各模块按日期取来
 */
@Data
public class CalendarVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 截止日在范围内的，加上今天之前过期没做完的（日视图、当天面板要列出来）
     */
    private List<TaskVO> tasks = List.of();

    private List<MeetingVO> meetings = List.of();

    /**
     * 未归档的习惯，打卡在前端按天对上
     */
    private List<HabitVO> habits = List.of();

    private List<HabitLogVO> logs = List.of();

    /**
     * 范围内写的日记（带「日记」标签的笔记），第一行里有心情
     */
    private List<NoteVO> journals = List.of();

    private List<FocusSessionVO> sessions = List.of();
}
