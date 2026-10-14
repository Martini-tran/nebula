package com.nebula.space.mcp;

import com.nebula.space.vo.me.FocusSessionVO;
import com.nebula.space.vo.me.MeetingVO;
import com.nebula.space.vo.me.NoteVO;
import com.nebula.space.vo.me.ReportVO;
import com.nebula.space.vo.me.TaskVO;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 写日报用的一天材料：任务按日报的段落预先分好组，口径与页面「插入记录」一致（前端 reportDraft.ts 的 dayDraft）
 *
 * @param date          哪一天
 * @param report        这一天已保存的日报，没写过为 null
 * @param doneTasks     这一天完成的任务，按完成时间先后
 * @param dueTasks      这一天到期、还没做完的
 * @param overdueTasks  这一天之前就到期、还没做完的，按到期日先后
 * @param nextDayTasks  下一天到期、还没做完的，优先级高的在前
 * @param meetings      这一天的会议（含正文，决议与待办写在正文里）
 * @param focusSessions 这一天的专注记录，taskId 对得上的可以在完成的任务后注明专注时长
 * @param focusMinutes  这一天专注总分钟数
 * @param notes         这一天写的笔记
 */
public record DayDigest(
        LocalDate date,
        ReportVO report,
        List<TaskVO> doneTasks,
        List<TaskVO> dueTasks,
        List<TaskVO> overdueTasks,
        List<TaskVO> nextDayTasks,
        List<MeetingVO> meetings,
        List<FocusSessionVO> focusSessions,
        int focusMinutes,
        List<NoteVO> notes) {

    /**
     * @param tasks 截止日在这一天到下一天之间的、这一天之前过期没做完的、这一天开始以后完成的
     */
    static DayDigest of(LocalDate date, ReportVO report, List<TaskVO> tasks, List<MeetingVO> meetings,
                        List<FocusSessionVO> focusSessions, List<NoteVO> notes) {
        LocalDate next = date.plusDays(1);
        List<TaskVO> done = tasks.stream()
                .filter(t -> isDone(t) && t.getDoneTime() != null && t.getDoneTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(TaskVO::getDoneTime))
                .toList();
        List<TaskVO> due = tasks.stream()
                .filter(t -> !isDone(t) && date.equals(t.getDueDate()))
                .toList();
        List<TaskVO> overdue = tasks.stream()
                .filter(t -> !isDone(t) && t.getDueDate() != null && t.getDueDate().isBefore(date))
                .sorted(Comparator.comparing(TaskVO::getDueDate))
                .toList();
        List<TaskVO> nextDay = tasks.stream()
                .filter(t -> !isDone(t) && next.equals(t.getDueDate()))
                .sorted(Comparator.comparing((TaskVO t) -> t.getPriority() == null ? 0 : t.getPriority()).reversed())
                .toList();
        int focusMinutes = focusSessions.stream()
                .map(FocusSessionVO::getActualMin)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return new DayDigest(date, report, done, due, overdue, nextDay, meetings, focusSessions, focusMinutes, notes);
    }

    private static boolean isDone(TaskVO task) {
        return Boolean.TRUE.equals(task.getDone());
    }
}
