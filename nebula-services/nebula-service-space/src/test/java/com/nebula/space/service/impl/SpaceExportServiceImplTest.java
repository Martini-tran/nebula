package com.nebula.space.service.impl;

import com.nebula.space.service.SpaceAnniversaryService;
import com.nebula.space.service.SpaceFileService;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceGoalService;
import com.nebula.space.service.SpaceHabitService;
import com.nebula.space.service.SpaceLedgerRecurringService;
import com.nebula.space.service.SpaceLedgerService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpacePersonService;
import com.nebula.space.service.SpaceProfileService;
import com.nebula.space.service.SpaceReadingService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceSettingService;
import com.nebula.space.service.SpaceShareService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.NoteVO;
import com.nebula.space.vo.me.PersonVO;
import com.nebula.space.vo.me.ReadingItemVO;
import com.nebula.space.vo.me.TaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceExportServiceImplTest {

    private SpaceTaskService taskService;
    private SpaceNoteService noteService;
    private SpaceHabitService habitService;
    private SpaceReadingService readingService;
    private SpaceLedgerService ledgerService;
    private SpaceGoalService goalService;
    private SpacePersonService personService;
    private SpaceExportServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(SpaceTaskService.class);
        noteService = mock(SpaceNoteService.class);
        habitService = mock(SpaceHabitService.class);
        readingService = mock(SpaceReadingService.class);
        ledgerService = mock(SpaceLedgerService.class);
        goalService = mock(SpaceGoalService.class);
        personService = mock(SpacePersonService.class);
        service = new SpaceExportServiceImpl(mock(SpaceSettingService.class), taskService, mock(SpaceTaskListService.class),
                noteService, mock(SpaceMeetingService.class), habitService, mock(SpaceFocusService.class),
                mock(SpaceReportService.class), readingService, ledgerService, mock(SpaceLedgerRecurringService.class),
                goalService, mock(SpaceAnniversaryService.class), personService, mock(SpaceFileService.class),
                mock(SpaceShareService.class), mock(SpaceProfileService.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void takesEverythingIncludingArchived() {
        when(taskService.list(argThat(q -> q != null && "all".equals(q.getView())))).thenReturn(List.of(new TaskVO(), new TaskVO()));
        when(noteService.list(argThat(q -> q != null && "all".equals(q.getView())))).thenReturn(List.of(new NoteVO()));
        when(noteService.list(argThat(q -> q != null && "archived".equals(q.getView())))).thenReturn(List.of(new NoteVO()));
        when(readingService.list(argThat(q -> q != null && Boolean.TRUE.equals(q.getArchived())))).thenReturn(List.of(new ReadingItemVO()));
        when(personService.list()).thenReturn(List.of(new PersonVO(), new PersonVO(), new PersonVO()));

        Map<String, Object> out = service.snapshot();

        assertEquals("nebula-space", out.get("app"));
        assertEquals(1, out.get("version"));
        assertEquals(2, ((List<?>) out.get("notes")).size());
        assertEquals(1, ((List<?>) out.get("reading")).size());
        Map<String, Integer> counts = (Map<String, Integer>) out.get("counts");
        assertEquals(2, counts.get("tasks"));
        assertEquals(3, counts.get("people"));
        assertEquals(2 + 2 + 1 + 3, out.get("total"));
        assertTrue(((Map<String, Object>) out.get("ledger")).containsKey("budgets"));
        // 归档的习惯、所有年份的目标都要
        verify(habitService).list(true);
        verify(goalService).listAll();
        verify(ledgerService).listBudgets();
    }
}
