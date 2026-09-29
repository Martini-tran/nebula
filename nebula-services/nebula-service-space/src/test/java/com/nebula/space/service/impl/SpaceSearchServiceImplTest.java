package com.nebula.space.service.impl;

import com.nebula.common.core.context.UserContext;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.service.SpaceBookmarkAdminService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpacePersonService;
import com.nebula.space.service.SpaceReadingService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.HighlightVO;
import com.nebula.space.vo.me.PersonVO;
import com.nebula.space.vo.me.ReadingItemVO;
import com.nebula.space.vo.me.SearchResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpaceSearchServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private SpaceTaskService taskService;
    private SpaceTaskListService taskListService;
    private SpaceNoteService noteService;
    private SpaceMeetingService meetingService;
    private SpaceReportService reportService;
    private SpaceReadingService readingService;
    private SpacePersonService personService;
    private SpaceBookmarkAdminService bookmarkService;
    private SpaceSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(SpaceTaskService.class);
        taskListService = mock(SpaceTaskListService.class);
        noteService = mock(SpaceNoteService.class);
        meetingService = mock(SpaceMeetingService.class);
        reportService = mock(SpaceReportService.class);
        readingService = mock(SpaceReadingService.class);
        personService = mock(SpacePersonService.class);
        bookmarkService = mock(SpaceBookmarkAdminService.class);
        service = new SpaceSearchServiceImpl(taskService, taskListService, noteService, meetingService,
                reportService, readingService, personService, bookmarkService);
        UserContext.set(42L, "me", Collections.emptyList(), List.of(SpaceSearchServiceImpl.BOOKMARK_LIST));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void emptyQueryTouchesNothing() {
        SearchResultVO out = service.search("   ", TODAY);
        assertTrue(out.getTasks().isEmpty());
        verifyNoInteractions(taskService, taskListService, noteService, meetingService, reportService,
                readingService, personService, bookmarkService);
    }

    @Test
    void plainWordsSearchEveryModule() {
        service.search("周报", TODAY);
        verify(taskService).search(any(), anyInt());
        verify(taskListService).list();
        verify(noteService).search(any(), anyInt());
        verify(bookmarkService).search(any(), anyInt());
        verify(meetingService).search(any(), anyInt());
        verify(readingService).search(any(), anyInt());
        verify(readingService).searchHighlights(any(), anyInt());
        verify(reportService).search(any(), anyInt());
        verify(personService).search(any(), anyInt());
        // 没有 @ 时不用去认人
        verify(personService, never()).list();
    }

    @Test
    void bookmarksNeedTheListPermission() {
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
        service.search("周报", TODAY);
        verifyNoInteractions(bookmarkService);
        verify(noteService).search(any(), anyInt());
    }

    @Test
    void stateSkipsModulesThatDoNotHaveOne() {
        service.search("is:done 周报", TODAY);
        verify(taskService).search(any(), anyInt());
        verify(meetingService).search(any(), anyInt());
        verify(readingService).search(any(), anyInt());
        verify(readingService, never()).searchHighlights(any(), anyInt());
        verifyNoInteractions(noteService, bookmarkService, reportService, personService);
    }

    @Test
    void tagSkipsMeetingsReadingAndReports() {
        service.search("#工作", TODAY);
        verify(taskService).search(any(), anyInt());
        verify(noteService).search(any(), anyInt());
        verify(bookmarkService).search(any(), anyInt());
        verify(personService).search(any(), anyInt());
        verifyNoInteractions(meetingService, readingService, reportService);
    }

    @Test
    void prefixLimitsToOneModule() {
        service.search("t: 周报", TODAY);
        verify(taskService).search(any(), anyInt());
        verify(taskListService).list();
        verifyNoInteractions(noteService, meetingService, reportService, readingService, personService, bookmarkService);
    }

    @Test
    void mentionExpandsToOtherNamesOnTheCard() {
        PersonVO zhang = person(7L, "张立", "张工", List.of("立哥", "李"));
        PersonVO other = person(8L, "王五", "", List.of());
        when(personService.list()).thenReturn(List.of(other, zhang));

        SearchResultVO out = service.search("@张工 评审", TODAY);

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(taskService).search(captor.capture(), anyInt());
        // 一个字的「李」不算叫法
        assertEquals(List.of("张工", "张立", "立哥"), captor.getValue().getPeopleNames());
        verify(meetingService).search(any(), anyInt());
        verifyNoInteractions(noteService, bookmarkService, readingService, reportService);
        // 卡片本身跟着返回，前端靠它认其他叫法
        assertEquals(List.of(7L), out.getPeople().stream().map(PersonVO::getId).toList());
    }

    @Test
    void highlightArticlesAreFilledIn() {
        ReadingItemVO found = new ReadingItemVO();
        found.setId(1L);
        ReadingItemVO parent = new ReadingItemVO();
        parent.setId(2L);
        when(readingService.search(any(), anyInt())).thenReturn(List.of(found));
        when(readingService.searchHighlights(any(), anyInt())).thenReturn(List.of(highlight(1L), highlight(2L), highlight(2L)));
        when(readingService.listByIds(any())).thenReturn(List.of(parent));

        SearchResultVO out = service.search("l: 复利", TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(readingService).listByIds(ids.capture());
        assertEquals(List.of(2L), List.copyOf(ids.getValue()));
        assertEquals(List.of(1L, 2L), out.getReading().stream().map(ReadingItemVO::getId).toList());
        assertEquals(3, out.getHighlights().size());
    }

    @Test
    void longInputIsCut() {
        service.search("x".repeat(500), TODAY);
        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(noteService).search(captor.capture(), anyInt());
        assertEquals(SpaceSearchServiceImpl.MAX_INPUT, captor.getValue().getTerms().get(0).length());
    }

    private static PersonVO person(Long id, String name, String alias, List<String> extra) {
        PersonVO p = new PersonVO();
        p.setId(id);
        p.setName(name);
        p.setAlias(alias);
        p.setExtraNames(extra);
        return p;
    }

    private static HighlightVO highlight(Long itemId) {
        HighlightVO h = new HighlightVO();
        h.setItemId(itemId);
        return h;
    }
}
