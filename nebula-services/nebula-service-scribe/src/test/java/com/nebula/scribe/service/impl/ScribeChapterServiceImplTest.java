package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.ChapterCreateRequest;
import com.nebula.scribe.dto.ChapterSaveRequest;
import com.nebula.scribe.dto.ChapterSortRequest;
import com.nebula.scribe.entity.ScribeChapter;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.mapper.ScribeChapterMapper;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.util.WordCounter;
import com.nebula.scribe.vo.ChapterDetailVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScribeChapterServiceImplTest {

    private ScribeChapterMapper chapterMapper;
    private ScribeWorkMapper workMapper;
    private ScribeChapterServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // Lambda 条件与 select 过滤需要实体的列缓存，纯 Mock 单测没有 Spring 容器来初始化它
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScribeWork.class);
        TableInfoHelper.initTableInfo(assistant, ScribeChapter.class);
    }

    @BeforeEach
    void setUp() {
        chapterMapper = mock(ScribeChapterMapper.class);
        workMapper = mock(ScribeWorkMapper.class);
        service = new ScribeChapterServiceImpl(chapterMapper, workMapper);
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void givenOwnedWork(int chapterCount) {
        ScribeWork work = new ScribeWork();
        work.setId(7L);
        work.setUserId(42L);
        work.setChapterCount(chapterCount);
        when(workMapper.selectOne(any(Wrapper.class))).thenReturn(work);
    }

    private ScribeChapter chapter(long revision, int words, String status) {
        ScribeChapter c = new ScribeChapter();
        c.setId(11L);
        c.setWorkId(7L);
        c.setTitle("第一章");
        c.setRevision(revision);
        c.setWordCount(words);
        c.setStatus(status);
        c.setContent("旧");
        return c;
    }

    @SuppressWarnings("unchecked")
    private String lastWorkUpdateSql() {
        ArgumentCaptor<Wrapper<ScribeWork>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workMapper).update(any(ScribeWork.class), captor.capture());
        return captor.getValue().getSqlSet();
    }

    @Test
    void wordCounterSkipsWhitespaceIncludingFullWidthSpace() {
        assertEquals(0, WordCounter.count(null));
        assertEquals(7, WordCounter.count("  他说：“走。”\n\n\t"));
        assertEquals(4, WordCounter.count("春　眠 不觉"));
        assertEquals(2, WordCounter.count("𠀀😀"), "扩展区汉字与 emoji 按 1 个字计");
    }

    @Test
    void createAppendsAfterLastAndNamesByCount() {
        givenOwnedWork(2);
        ScribeChapter last = new ScribeChapter();
        last.setSortOrder(3000);
        when(chapterMapper.selectOne(any(Wrapper.class))).thenReturn(last);
        doAnswer(inv -> {
            inv.<ScribeChapter>getArgument(0).setId(99L);
            return 1;
        }).when(chapterMapper).insert(any(ScribeChapter.class));

        ChapterDetailVO vo = service.create(7L, new ChapterCreateRequest());

        ArgumentCaptor<ScribeChapter> captor = ArgumentCaptor.forClass(ScribeChapter.class);
        verify(chapterMapper).insert(captor.capture());
        ScribeChapter saved = captor.getValue();
        assertEquals(7L, saved.getWorkId());
        assertEquals("第3章", saved.getTitle());
        assertEquals(4000, saved.getSortOrder());
        assertEquals("outline", saved.getStatus());
        assertEquals(0L, saved.getRevision());
        assertEquals(99L, vo.getId());
        assertEquals("", vo.getContent());
        assertTrue(lastWorkUpdateSql().contains("chapter_count = GREATEST(chapter_count +"));
    }

    @Test
    void operationsOnOthersWorkAreNotFound() {
        when(workMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.list(7L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
        verify(chapterMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void saveRejectsStaleRevisionWithoutWriting() {
        givenOwnedWork(1);
        when(chapterMapper.selectOne(any(Wrapper.class))).thenReturn(chapter(5, 10, "drafting"));
        ChapterSaveRequest req = new ChapterSaveRequest();
        req.setRevision(4L);
        req.setContent("新正文");

        BizException e = assertThrows(BizException.class, () -> service.save(7L, 11L, req));
        assertEquals(HttpStatus.CONFLICT, e.getCode());
        verify(chapterMapper, never()).update(any(ScribeChapter.class), any(Wrapper.class));
    }

    @Test
    void saveConflictsWhenConcurrentWriteWins() {
        givenOwnedWork(1);
        when(chapterMapper.selectOne(any(Wrapper.class))).thenReturn(chapter(5, 10, "drafting"));
        // 读到时修订号一致，但落库时已被另一处抢先 +1
        when(chapterMapper.update(any(ScribeChapter.class), any(Wrapper.class))).thenReturn(0);
        ChapterSaveRequest req = new ChapterSaveRequest();
        req.setRevision(5L);
        req.setContent("新正文");

        BizException e = assertThrows(BizException.class, () -> service.save(7L, 11L, req));
        assertEquals(HttpStatus.CONFLICT, e.getCode());
        verify(workMapper, never()).update(any(ScribeWork.class), any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveContentCountsWordsBumpsRevisionAndSyncsWork() {
        givenOwnedWork(1);
        when(chapterMapper.selectOne(any(Wrapper.class))).thenReturn(chapter(5, 10, "outline"));
        when(chapterMapper.update(any(ScribeChapter.class), any(Wrapper.class))).thenReturn(1);
        when(chapterMapper.selectById(11L)).thenReturn(chapter(6, 4, "drafting"));
        ChapterSaveRequest req = new ChapterSaveRequest();
        req.setRevision(5L);
        req.setContent("夜 雨\n孤灯");

        ChapterDetailVO vo = service.save(7L, 11L, req);

        ArgumentCaptor<Wrapper<ScribeChapter>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chapterMapper).update(any(ScribeChapter.class), captor.capture());
        LambdaUpdateWrapper<ScribeChapter> wrapper = (LambdaUpdateWrapper<ScribeChapter>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("revision = revision + 1"));
        assertTrue(sqlSet.contains("word_count="), sqlSet);
        assertTrue(sqlSet.contains("status="), "大纲状态首次写正文应自动转草稿");
        assertTrue(sqlSet.contains("title=") == false, "未传标题不应改标题");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(4));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("drafting"));
        assertTrue(wrapper.getSqlSegment().contains("revision"), "修订号比对应在 where 里");

        // 10 字改成 4 字，作品累计字数 -6
        assertTrue(lastWorkUpdateSql().contains("word_count = GREATEST(word_count +"));
        assertEquals(6L, vo.getRevision());
    }

    @Test
    void saveMetaOnlyDoesNotTouchWorkCounters() {
        givenOwnedWork(1);
        when(chapterMapper.selectOne(any(Wrapper.class))).thenReturn(chapter(5, 10, "drafting"));
        when(chapterMapper.update(any(ScribeChapter.class), any(Wrapper.class))).thenReturn(1);
        when(chapterMapper.selectById(11L)).thenReturn(chapter(6, 10, "drafting"));
        ChapterSaveRequest req = new ChapterSaveRequest();
        req.setRevision(5L);
        req.setTitle(" 夜雨 ");

        service.save(7L, 11L, req);

        verify(workMapper, never()).update(any(ScribeWork.class), any(Wrapper.class));
    }

    @Test
    void saveRejectsBlankTitleAndUnknownStatus() {
        ChapterSaveRequest blank = new ChapterSaveRequest();
        blank.setRevision(1L);
        blank.setTitle("  ");
        assertThrows(BizException.class, () -> service.save(7L, 11L, blank));

        ChapterSaveRequest bad = new ChapterSaveRequest();
        bad.setRevision(1L);
        bad.setStatus("published");
        assertThrows(BizException.class, () -> service.save(7L, 11L, bad));
    }

    @Test
    void sortRequiresExactlyAllChaptersOfWork() {
        givenOwnedWork(2);
        ScribeChapter a = new ScribeChapter();
        a.setId(1L);
        ScribeChapter b = new ScribeChapter();
        b.setId(2L);
        when(chapterMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));

        ChapterSortRequest partial = new ChapterSortRequest();
        partial.setIds(List.of(2L));
        assertThrows(BizException.class, () -> service.sort(7L, partial));

        ChapterSortRequest foreign = new ChapterSortRequest();
        foreign.setIds(List.of(2L, 3L));
        assertThrows(BizException.class, () -> service.sort(7L, foreign));
        verify(chapterMapper, never()).update(any(ScribeChapter.class), any(Wrapper.class));

        ChapterSortRequest ok = new ChapterSortRequest();
        ok.setIds(List.of(2L, 1L));
        service.sort(7L, ok);
        verify(chapterMapper, times(2)).update(any(ScribeChapter.class), any(Wrapper.class));
    }

    @Test
    void deleteSoftDeletesAndSubtractsCounters() {
        givenOwnedWork(1);
        when(chapterMapper.selectOne(any(Wrapper.class))).thenReturn(chapter(5, 10, "drafting"));

        service.delete(7L, 11L);

        verify(chapterMapper).updateById(any(ScribeChapter.class));
        verify(chapterMapper).deleteById(11L);
        String sql = lastWorkUpdateSql();
        assertTrue(sql.contains("chapter_count") && sql.contains("word_count"), sql);
    }
}
