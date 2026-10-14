package com.nebula.space.mcp;

import com.nebula.space.vo.me.FocusSessionVO;
import com.nebula.space.vo.me.TaskVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DayDigestTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    private static TaskVO task(String title, LocalDate due, LocalDateTime doneTime, int priority) {
        TaskVO t = new TaskVO();
        t.setTitle(title);
        t.setDueDate(due);
        t.setDone(doneTime != null);
        t.setDoneTime(doneTime);
        t.setPriority(priority);
        return t;
    }

    private static FocusSessionVO session(Integer minutes) {
        FocusSessionVO s = new FocusSessionVO();
        s.setActualMin(minutes);
        return s;
    }

    private static List<String> titles(List<TaskVO> tasks) {
        return tasks.stream().map(TaskVO::getTitle).toList();
    }

    @Test
    void groupsTasksLikeTheDailyDraft() {
        List<TaskVO> tasks = List.of(
                task("下午完成", DAY, DAY.atTime(16, 0), 0),
                task("上午完成", null, DAY.atTime(9, 30), 0),
                task("第二天才完成", DAY, DAY.plusDays(1).atTime(10, 0), 0),
                task("今天到期", DAY, null, 0),
                task("逾期两天", DAY.minusDays(2), null, 0),
                task("逾期五天", DAY.minusDays(5), null, 0),
                task("明天低优先", DAY.plusDays(1), null, 1),
                task("明天高优先", DAY.plusDays(1), null, 3));

        DayDigest digest = DayDigest.of(DAY, null, tasks, List.of(), List.of(), List.of());

        assertEquals(List.of("上午完成", "下午完成"), titles(digest.doneTasks()));
        assertEquals(List.of("今天到期"), titles(digest.dueTasks()));
        assertEquals(List.of("逾期五天", "逾期两天"), titles(digest.overdueTasks()));
        assertEquals(List.of("明天高优先", "明天低优先"), titles(digest.nextDayTasks()));
    }

    @Test
    void sumsFocusMinutesIgnoringMissing() {
        DayDigest digest = DayDigest.of(DAY, null, List.of(), List.of(),
                List.of(session(25), session(null), session(50)), List.of());

        assertEquals(75, digest.focusMinutes());
    }
}
