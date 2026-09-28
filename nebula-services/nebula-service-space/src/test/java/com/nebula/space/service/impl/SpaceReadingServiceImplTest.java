package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.HighlightCreateRequest;
import com.nebula.space.dto.me.HighlightSaveRequest;
import com.nebula.space.dto.me.ReadingCreateRequest;
import com.nebula.space.dto.me.ReadingSaveRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceNote;
import com.nebula.space.entity.SpaceReading;
import com.nebula.space.entity.SpaceReadingHighlight;
import com.nebula.space.entity.SpaceTask;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceNoteMapper;
import com.nebula.space.mapper.SpaceReadingHighlightMapper;
import com.nebula.space.mapper.SpaceReadingMapper;
import com.nebula.space.mapper.SpaceTaskMapper;
import com.nebula.space.reading.ArticleExtractor;
import com.nebula.space.reading.ArticleFetcher;
import com.nebula.space.vo.me.HighlightVO;
import com.nebula.space.vo.me.ReadingItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceReadingServiceImplTest {

    private static final String CONTENT = "[\"第一段的内容，比较长一些。\",\"第二段\"]";

    private SpaceReadingMapper readingMapper;
    private SpaceReadingHighlightMapper highlightMapper;
    private SpaceBookmarkMapper bookmarkMapper;
    private SpaceNoteMapper noteMapper;
    private SpaceTaskMapper taskMapper;
    private ArticleFetcher fetcher;
    private SpaceReadingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceReading.class);
        TableInfoHelper.initTableInfo(assistant, SpaceReadingHighlight.class);
        TableInfoHelper.initTableInfo(assistant, SpaceBookmark.class);
        TableInfoHelper.initTableInfo(assistant, SpaceNote.class);
        TableInfoHelper.initTableInfo(assistant, SpaceTask.class);
    }

    @BeforeEach
    void setUp() {
        readingMapper = mock(SpaceReadingMapper.class);
        highlightMapper = mock(SpaceReadingHighlightMapper.class);
        bookmarkMapper = mock(SpaceBookmarkMapper.class);
        noteMapper = mock(SpaceNoteMapper.class);
        taskMapper = mock(SpaceTaskMapper.class);
        fetcher = mock(ArticleFetcher.class);
        service = new SpaceReadingServiceImpl(readingMapper, highlightMapper, bookmarkMapper, noteMapper, taskMapper, fetcher);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static SpaceReading item(String content, int archived) {
        SpaceReading r = new SpaceReading();
        r.setId(5L);
        r.setUserId(42L);
        r.setUrl("https://www.example.com/post");
        r.setTitle("example.com");
        r.setExcerpt("");
        r.setContent(content);
        r.setReadMinutes(content == null ? 0 : 3);
        r.setReadStatus("unread");
        r.setReadProgress(0.0);
        r.setReadPosition(0.0);
        r.setThought("");
        r.setArchived(archived);
        return r;
    }

    private static ReadingCreateRequest add(String url) {
        ReadingCreateRequest req = new ReadingCreateRequest();
        req.setUrl(url);
        return req;
    }

    // ----------------------------------------------------------------- 加入

    @Test
    void createStoresFetchedArticle() {
        when(fetcher.fetch(anyString())).thenReturn(new ArticleExtractor.Article("网页标题", "开头的摘要", List.of("第一段", "第二段"), 4));
        ReadingItemVO vo = service.create(add(" https://www.Example.com/post "));
        ArgumentCaptor<SpaceReading> saved = ArgumentCaptor.forClass(SpaceReading.class);
        verify(readingMapper).insert(saved.capture());
        SpaceReading r = saved.getValue();
        assertEquals("https://www.Example.com/post", r.getUrl());
        assertEquals("[\"第一段\",\"第二段\"]", r.getContent());
        assertEquals(64, r.getUrlHash().length());
        assertEquals("网页标题", vo.getTitle());
        assertEquals("example.com", vo.getDomain());
        assertEquals("开头的摘要", vo.getExcerpt());
        assertEquals(4, vo.getMinutes());
        assertTrue(vo.getSaved());
        // 列表、加入的返回都不带正文
        assertNull(vo.getContent());
        assertEquals("unread", vo.getStatus());
    }

    @Test
    void createKeepsGivenTitleAndSurvivesFetchFailure() {
        ReadingCreateRequest req = add("https://example.com/a");
        req.setTitle("书签里的标题");
        ReadingItemVO vo = service.create(req);
        assertEquals("书签里的标题", vo.getTitle());
        assertFalse(vo.getSaved());
        assertEquals("", vo.getExcerpt());
        assertEquals(0, vo.getMinutes());
    }

    @Test
    void sameUrlReturnsExistingAndPutsItBackInQueue() {
        SpaceReading existing = item(CONTENT, 1);
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        ReadingItemVO vo = service.create(add("https://www.example.com/post#section"));
        assertFalse(vo.getArchived());
        assertTrue(vo.getSaved());
        verify(readingMapper, never()).insert(any(SpaceReading.class));
        verify(readingMapper).updateById(existing);
        // 已经存档的不重抓
        verify(fetcher, never()).fetch(anyString());
    }

    @Test
    void sameUrlWithoutContentIsFetchedAgain() {
        SpaceReading existing = item(null, 0);
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(fetcher.fetch(anyString())).thenReturn(new ArticleExtractor.Article("终于抓到了", "摘要", List.of("正文"), 1));
        ReadingItemVO vo = service.create(add("https://www.example.com/post"));
        assertTrue(vo.getSaved());
        assertEquals("终于抓到了", vo.getTitle());
        verify(readingMapper).updateById(existing);
    }

    @Test
    void foreignBookmarkIsDropped() {
        when(bookmarkMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        ReadingCreateRequest req = add("https://example.com/a");
        req.setBookmarkId(99L);
        assertNull(service.create(req).getBookmarkId());
    }

    @Test
    void onlyHttpUrlsAreAccepted() {
        for (String url : new String[]{"ftp://example.com/a", "javascript:alert(1)", "example.com", "https://", "http://exa mple.com/%"}) {
            BizException e = assertThrows(BizException.class, () -> service.create(add(url)), url);
            assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        }
    }

    @Test
    void normalizeIgnoresFragmentAndCase() {
        assertEquals("https://example.com/", SpaceReadingServiceImpl.normalizeUrl("HTTPS://Example.COM#top"));
        assertEquals("https://example.com/a?b=1", SpaceReadingServiceImpl.normalizeUrl("https://EXAMPLE.com/a?b=1#c"));
        assertEquals("sspai.com", SpaceReadingServiceImpl.domainOf("https://www.sspai.com/post/1"));
    }

    @Test
    void refetchRefusesArchivedContent() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(CONTENT, 0));
        BizException e = assertThrows(BizException.class, () -> service.refetch(5L));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(fetcher, never()).fetch(anyString());
    }

    @Test
    void getReturnsContent() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(CONTENT, 0));
        ReadingItemVO vo = service.get(5L);
        assertEquals(List.of("第一段的内容，比较长一些。", "第二段"), vo.getContent());
        assertTrue(vo.getSaved());
    }

    @Test
    void foreignItemIs404() {
        BizException e = assertThrows(BizException.class, () -> service.get(5L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    // ----------------------------------------------------------------- 阅读状态

    @Test
    void markingDoneFillsProgressAndTime() {
        SpaceReading r = item(null, 0);
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(r);
        ReadingSaveRequest req = new ReadingSaveRequest();
        req.setStatus("done");
        req.setThought(" 想清楚了 ");
        ReadingItemVO vo = service.update(5L, req);
        assertEquals(1.0, vo.getProgress());
        assertNotNull(vo.getDoneTime());
        assertEquals("想清楚了", vo.getThought());

        ReadingSaveRequest back = new ReadingSaveRequest();
        back.setStatus("reading");
        assertNull(service.update(5L, back).getDoneTime());
    }

    @Test
    void doneTimeFromClientIsKept() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(null, 0));
        ReadingSaveRequest req = new ReadingSaveRequest();
        req.setStatus("done");
        req.setDoneTime("2026-09-27 22:05:00");
        req.setLastReadTime("2026-09-27 22:05:00");
        ReadingItemVO vo = service.update(5L, req);
        assertEquals(LocalDateTime.of(2026, 9, 27, 22, 5), vo.getDoneTime());
        assertEquals(LocalDateTime.of(2026, 9, 27, 22, 5), vo.getLastReadTime());
    }

    @Test
    void deleteRemovesHighlightsFirst() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(CONTENT, 0));
        service.delete(5L);
        InOrder order = inOrder(highlightMapper, readingMapper);
        order.verify(highlightMapper).delete(any(Wrapper.class));
        order.verify(readingMapper).deleteById(5L);
    }

    // ----------------------------------------------------------------- 划线

    private static HighlightCreateRequest mark(int para, int start, int end) {
        HighlightCreateRequest req = new HighlightCreateRequest();
        req.setItemId(5L);
        req.setPara(para);
        req.setStart(start);
        req.setEnd(end);
        req.setText("前端传来的不作数");
        return req;
    }

    @Test
    void highlightTextComesFromArchivedParagraph() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(CONTENT, 0));
        HighlightVO vo = service.createHighlight(mark(0, 0, 3));
        assertEquals("第一段", vo.getText());
        assertEquals("yellow", vo.getColor());
        assertEquals("", vo.getNote());
    }

    @Test
    void highlightOutsideParagraphIs400() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(CONTENT, 0));
        for (HighlightCreateRequest req : List.of(mark(2, 0, 1), mark(1, 0, 4), mark(0, 3, 3))) {
            BizException e = assertThrows(BizException.class, () -> service.createHighlight(req));
            assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        }
        verify(highlightMapper, never()).insert(any(SpaceReadingHighlight.class));
    }

    @Test
    void overlappingHighlightIs400() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(CONTENT, 0));
        when(highlightMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> service.createHighlight(mark(0, 0, 3)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void cannotHighlightWithoutArchivedContent() {
        when(readingMapper.selectOne(any(Wrapper.class))).thenReturn(item(null, 0));
        BizException e = assertThrows(BizException.class, () -> service.createHighlight(mark(0, 0, 3)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void linkingSomeoneElsesNoteIs400() {
        SpaceReadingHighlight h = new SpaceReadingHighlight();
        h.setId(8L);
        h.setUserId(42L);
        when(highlightMapper.selectOne(any(Wrapper.class))).thenReturn(h);
        when(noteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        HighlightSaveRequest req = new HighlightSaveRequest();
        req.setNoteId(77L);
        BizException e = assertThrows(BizException.class, () -> service.updateHighlight(8L, req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(highlightMapper, never()).updateById(any(SpaceReadingHighlight.class));
    }
}
