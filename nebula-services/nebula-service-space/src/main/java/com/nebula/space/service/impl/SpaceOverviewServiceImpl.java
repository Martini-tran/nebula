package com.nebula.space.service.impl;

import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.FocusSessionQuery;
import com.nebula.space.dto.me.HabitLogQuery;
import com.nebula.space.dto.me.MeetingQuery;
import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.service.SpaceBookmarkAdminService;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceHabitService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpaceOverviewService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.CalendarVO;
import com.nebula.space.vo.me.NoteVO;
import com.nebula.space.vo.me.TodayVO;
import com.nebula.space.vo.me.WeekReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 「今天」页与日历的聚合实现：复用各模块服务的查询，只是把取数范围收窄到那几天
 */
@Service
@RequiredArgsConstructor
public class SpaceOverviewServiceImpl implements SpaceOverviewService {

    /**
     * 日历一次最多取多少天（月视图连前后补齐是 42 天）
     */
    static final int MAX_DAYS = 100;
    /**
     * 「今天」页「最近的随手记」显示几条
     */
    static final int RECENT_NOTES = 5;
    static final int TODAY_BOOKMARKS = 50;
    static final String JOURNAL_TAG = "日记";
    static final String BOOKMARK_LIST = "space:bookmark:list";
    static final String WEEK_REPORT = "week";

    private static final Comparator<NoteVO> RECENT_FIRST = Comparator
            .comparing(NoteVO::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(NoteVO::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final SpaceTaskService taskService;
    private final SpaceMeetingService meetingService;
    private final SpaceNoteService noteService;
    private final SpaceHabitService habitService;
    private final SpaceFocusService focusService;
    private final SpaceBookmarkAdminService bookmarkService;
    private final SpaceTaskListService taskListService;
    private final SpaceReportService reportService;

    @Override
    public TodayVO today(LocalDate date, LocalDate weekStart) {
        LocalDate day = date == null ? LocalDate.now() : date;
        // 本周第一天只能在今天及之前 6 天内，传错了按周一算
        LocalDate week = weekStart == null || weekStart.isAfter(day) || weekStart.isBefore(day.minusDays(6))
                ? day.minusDays(day.getDayOfWeek().getValue() - 1L)
                : weekStart;

        TodayVO vo = new TodayVO();
        vo.setTasks(taskService.listForDays(day, day, day, week.atStartOfDay()));
        vo.setMeetings(meetingService.list(meetingQuery(day, day)));

        NoteQuery written = new NoteQuery();
        written.setFrom(day);
        written.setTo(day);
        NoteQuery recent = new NoteQuery();
        recent.setLimit(RECENT_NOTES);
        vo.setNotes(union(noteService.list(written), noteService.list(recent)));

        // 与书签列表接口同样要权限，没有就不显示「今天收藏」
        if (UserContext.hasPermission(BOOKMARK_LIST)) {
            LocalDateTime start = day.atStartOfDay();
            vo.setBookmarks(bookmarkService.createdBetween(start, start.plusDays(1), TODAY_BOOKMARKS));
        }
        return vo;
    }

    @Override
    public CalendarVO calendar(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "日期范围不正确");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BizException(HttpStatus.BAD_REQUEST, "日历一次最多查 " + MAX_DAYS + " 天");
        }
        LocalDate day = today == null ? LocalDate.now() : today;

        CalendarVO vo = new CalendarVO();
        vo.setTasks(taskService.listForDays(from, to, day, null));
        vo.setMeetings(meetingService.list(meetingQuery(from, to)));
        vo.setHabits(habitService.list(false));

        HabitLogQuery logs = new HabitLogQuery();
        logs.setFrom(from);
        logs.setTo(to);
        vo.setLogs(habitService.logs(logs));

        NoteQuery journals = new NoteQuery();
        journals.setTag(JOURNAL_TAG);
        journals.setFrom(from);
        journals.setTo(to);
        vo.setJournals(noteService.list(journals));

        FocusSessionQuery sessions = new FocusSessionQuery();
        sessions.setFrom(from);
        sessions.setTo(to);
        vo.setSessions(focusService.list(sessions));
        return vo;
    }

    @Override
    public WeekReviewVO weekReview(LocalDate start) {
        if (start == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请指定是哪一周");
        }
        LocalDate prevStart = start.minusDays(7);
        LocalDate end = start.plusDays(6);

        WeekReviewVO vo = new WeekReviewVO();
        vo.setTasks(taskService.listOpenOrDoneSince(prevStart.atStartOfDay()));
        vo.setLists(taskListService.list());
        vo.setMeetings(meetingService.list(meetingQuery(prevStart, end)));

        FocusSessionQuery sessions = new FocusSessionQuery();
        sessions.setFrom(prevStart);
        sessions.setTo(end);
        vo.setSessions(focusService.list(sessions));

        vo.setHabits(habitService.list(true));
        HabitLogQuery logs = new HabitLogQuery();
        logs.setFrom(prevStart);
        logs.setTo(end);
        vo.setLogs(habitService.logs(logs));

        NoteQuery live = new NoteQuery();
        live.setFrom(start);
        live.setTo(end);
        NoteQuery archived = new NoteQuery();
        archived.setView("archived");
        archived.setFrom(start);
        archived.setTo(end);
        vo.setNotes(union(noteService.list(live), noteService.list(archived)));

        vo.setLastReport(reportService.get(WEEK_REPORT, prevStart));
        return vo;
    }

    private static MeetingQuery meetingQuery(LocalDate from, LocalDate to) {
        MeetingQuery q = new MeetingQuery();
        q.setFrom(from);
        q.setTo(to);
        return q;
    }

    /**
     * 两份笔记合并去重，仍按最近改过的在前（前端取前几条当「最近的随手记」）
     */
    private static List<NoteVO> union(List<NoteVO> a, List<NoteVO> b) {
        Map<Long, NoteVO> byId = new LinkedHashMap<>();
        Stream.concat(a.stream(), b.stream()).forEach(n -> byId.putIfAbsent(n.getId(), n));
        return byId.values().stream().sorted(RECENT_FIRST).toList();
    }
}
