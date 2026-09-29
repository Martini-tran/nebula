package com.nebula.space.service.impl;

import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.service.SpaceBookmarkAdminService;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceHabitService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.NoteVO;
import com.nebula.space.vo.me.TodayVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpaceOverviewServiceImplTest {

    /**
     * 周三，这周一是 9-28
     */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    private SpaceTaskService taskService;
    private SpaceMeetingService meetingService;
    private SpaceNoteService noteService;
    private SpaceHabitService habitService;
    private SpaceFocusService focusService;
    private SpaceBookmarkAdminService bookmarkService;
    private SpaceReportService reportService;
    private SpaceOverviewServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(SpaceTaskService.class);
        meetingService = mock(SpaceMeetingService.class);
        noteService = mock(SpaceNoteService.class);
        habitService = mock(SpaceHabitService.class);
        focusService = mock(SpaceFocusService.class);
        bookmarkService = mock(SpaceBookmarkAdminService.class);
        reportService = mock(SpaceReportService.class);
        service = new SpaceOverviewServiceImpl(taskService, meetingService, noteService, habitService, focusService, bookmarkService,
                mock(SpaceTaskListService.class), reportService);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static NoteVO note(long id, String updated) {
        NoteVO n = new NoteVO();
        n.setId(id);
        n.setUpdateTime(LocalDateTime.parse(updated));
        return n;
    }

    @Test
    void todayTakesTasksDoneSinceWeekStart() {
        service.today(TODAY, LocalDate.of(2026, 9, 27));
        verify(taskService).listForDays(TODAY, TODAY, TODAY, LocalDate.of(2026, 9, 27).atStartOfDay());
    }

    @Test
    void todayFallsBackToMondayWhenWeekStartIsOff() {
        service.today(TODAY, LocalDate.of(2026, 9, 1));
        verify(taskService).listForDays(TODAY, TODAY, TODAY, MONDAY.atStartOfDay());
    }

    @Test
    void todayNotesMergeWrittenTodayWithRecent() {
        when(noteService.list(argThat(q -> q != null && TODAY.equals(q.getFrom()))))
                .thenReturn(List.of(note(3, "2026-09-30T08:00:00"), note(1, "2026-09-30T12:00:00")));
        when(noteService.list(argThat(q -> q != null && q.getLimit() != null)))
                .thenReturn(List.of(note(1, "2026-09-30T12:00:00"), note(9, "2026-09-30T10:00:00")));

        TodayVO vo = service.today(TODAY, MONDAY);

        assertEquals(List.of(1L, 9L, 3L), vo.getNotes().stream().map(NoteVO::getId).toList());
    }

    @Test
    void todayBookmarksNeedThePermission() {
        TodayVO vo = service.today(TODAY, MONDAY);
        assertNull(vo.getBookmarks());
        verifyNoInteractions(bookmarkService);

        UserContext.set(42L, "me", Collections.emptyList(), List.of(SpaceOverviewServiceImpl.BOOKMARK_LIST));
        service.today(TODAY, MONDAY);
        verify(bookmarkService).createdBetween(TODAY.atStartOfDay(), TODAY.plusDays(1).atStartOfDay(), SpaceOverviewServiceImpl.TODAY_BOOKMARKS);
    }

    @Test
    void calendarTakesTheRangeAndJournals() {
        LocalDate from = LocalDate.of(2026, 8, 31);
        LocalDate to = LocalDate.of(2026, 10, 11);

        service.calendar(from, to, TODAY);

        verify(taskService).listForDays(eq(from), eq(to), eq(TODAY), isNull());
        verify(habitService).list(false);
        ArgumentCaptor<NoteQuery> journals = ArgumentCaptor.forClass(NoteQuery.class);
        verify(noteService).list(journals.capture());
        assertEquals(SpaceOverviewServiceImpl.JOURNAL_TAG, journals.getValue().getTag());
        assertEquals(from, journals.getValue().getFrom());
        assertEquals(to, journals.getValue().getTo());
        verify(focusService).list(argThat(q -> from.equals(q.getFrom()) && to.equals(q.getTo())));
        verify(meetingService).list(argThat(q -> from.equals(q.getFrom()) && to.equals(q.getTo())));
    }

    @Test
    void weekReviewTakesThisAndLastWeek() {
        when(noteService.list(argThat(q -> q != null && q.getView() == null)))
                .thenReturn(List.of(note(2, "2026-09-29T08:00:00")));
        when(noteService.list(argThat(q -> q != null && "archived".equals(q.getView()))))
                .thenReturn(List.of(note(5, "2026-09-30T08:00:00")));

        var vo = service.weekReview(MONDAY);

        verify(taskService).listOpenOrDoneSince(MONDAY.minusDays(7).atStartOfDay());
        verify(meetingService).list(argThat(q -> MONDAY.minusDays(7).equals(q.getFrom()) && MONDAY.plusDays(6).equals(q.getTo())));
        verify(habitService).list(true);
        verify(noteService).list(argThat(q -> q != null && "archived".equals(q.getView()) && MONDAY.equals(q.getFrom())));
        verify(reportService).get("week", MONDAY.minusDays(7));
        assertEquals(List.of(5L, 2L), vo.getNotes().stream().map(NoteVO::getId).toList());
    }

    @Test
    void calendarRejectsBadRanges() {
        BizException reversed = assertThrows(BizException.class, () -> service.calendar(TODAY, MONDAY, TODAY));
        assertEquals(HttpStatus.BAD_REQUEST, reversed.getCode());
        assertThrows(BizException.class, () -> service.calendar(MONDAY, MONDAY.plusDays(SpaceOverviewServiceImpl.MAX_DAYS), TODAY));
        verify(taskService, never()).listForDays(any(), any(), any(), any());
    }
}
