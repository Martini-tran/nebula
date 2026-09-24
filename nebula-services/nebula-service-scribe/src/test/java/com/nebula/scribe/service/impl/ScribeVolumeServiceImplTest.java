package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.TocSortRequest;
import com.nebula.scribe.dto.VolumeSaveRequest;
import com.nebula.scribe.entity.ScribeChapter;
import com.nebula.scribe.entity.ScribeVolume;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.mapper.ScribeChapterMapper;
import com.nebula.scribe.mapper.ScribeVolumeMapper;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.service.ScribeWorkGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScribeVolumeServiceImplTest {

    private ScribeVolumeMapper volumeMapper;
    private ScribeChapterMapper chapterMapper;
    private ScribeWorkMapper workMapper;
    private ScribeVolumeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScribeWork.class);
        TableInfoHelper.initTableInfo(assistant, ScribeChapter.class);
        TableInfoHelper.initTableInfo(assistant, ScribeVolume.class);
    }

    @BeforeEach
    void setUp() {
        volumeMapper = mock(ScribeVolumeMapper.class);
        chapterMapper = mock(ScribeChapterMapper.class);
        workMapper = mock(ScribeWorkMapper.class);
        service = new ScribeVolumeServiceImpl(volumeMapper, chapterMapper, new ScribeWorkGuard(workMapper));
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        ScribeWork work = new ScribeWork();
        work.setId(7L);
        work.setUserId(42L);
        when(workMapper.selectOne(any(Wrapper.class))).thenReturn(work);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static ScribeVolume volume(long id, int sort) {
        ScribeVolume v = new ScribeVolume();
        v.setId(id);
        v.setWorkId(7L);
        v.setTitle("卷" + id);
        v.setSortOrder(sort);
        return v;
    }

    private static ScribeChapter chapter(long id, Long volumeId, int sort) {
        ScribeChapter c = new ScribeChapter();
        c.setId(id);
        c.setWorkId(7L);
        c.setVolumeId(volumeId);
        c.setSortOrder(sort);
        return c;
    }

    /**
     * 收集所有「调整章节结构」的更新（entity 为 null 的那类），按 SQL 片段 + 参数展开，便于断言
     */
    @SuppressWarnings("unchecked")
    private List<LambdaUpdateWrapper<ScribeChapter>> chapterMoves() {
        ArgumentCaptor<Wrapper<ScribeChapter>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chapterMapper, org.mockito.Mockito.atLeast(0)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<ScribeChapter>> list = new ArrayList<>();
        captor.getAllValues().forEach(w -> list.add((LambdaUpdateWrapper<ScribeChapter>) w));
        return list;
    }

    private static boolean movedTo(LambdaUpdateWrapper<ScribeChapter> w, long chapterId, long volumeId, int sort) {
        // where 条件的参数是渲染 SQL 片段时才放进参数表的，先渲染一次再取
        w.getSqlSegment();
        Map<String, Object> p = w.getParamNameValuePairs();
        return p.containsValue(chapterId) && p.containsValue(sort) && p.containsValue(volumeId);
    }

    @Test
    void firstVolumeAdoptsAllLooseChaptersKeepingUpdateTime() {
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        doAnswer(inv -> {
            inv.<ScribeVolume>getArgument(0).setId(50L);
            return 1;
        }).when(volumeMapper).insert(any(ScribeVolume.class));

        service.create(7L, new VolumeSaveRequest());

        ArgumentCaptor<ScribeVolume> captor = ArgumentCaptor.forClass(ScribeVolume.class);
        verify(volumeMapper).insert(captor.capture());
        assertEquals("第1卷", captor.getValue().getTitle());
        assertEquals(1000, captor.getValue().getSortOrder());

        List<LambdaUpdateWrapper<ScribeChapter>> moves = chapterMoves();
        assertEquals(1, moves.size());
        LambdaUpdateWrapper<ScribeChapter> adopt = moves.getFirst();
        assertTrue(adopt.getSqlSegment().contains("volume_id IS NULL"), adopt.getSqlSegment());
        assertTrue(adopt.getParamNameValuePairs().containsValue(50L));
        assertTrue(adopt.getSqlSet().contains("update_time = update_time"), "收编不算编辑，不应刷新章节 update_time");
    }

    @Test
    void laterVolumeIsAppendedEmpty() {
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(volume(1, 1000), volume(2, 2000)));

        VolumeSaveRequest req = new VolumeSaveRequest();
        req.setTitle("  第三卷 · 归途 ");
        service.create(7L, req);

        ArgumentCaptor<ScribeVolume> captor = ArgumentCaptor.forClass(ScribeVolume.class);
        verify(volumeMapper).insert(captor.capture());
        assertEquals("第三卷 · 归途", captor.getValue().getTitle());
        assertEquals(3000, captor.getValue().getSortOrder());
        verify(chapterMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void deleteMiddleVolumeMergesIntoPreviousTail() {
        when(volumeMapper.selectOne(any(Wrapper.class))).thenReturn(volume(2, 2000));
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(volume(1, 1000), volume(2, 2000), volume(3, 3000)));
        // 第一次查被删卷的章节，第二次查上一卷的章节，之后是返回目录时的全量查询
        when(chapterMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(chapter(21, 2L, 1000), chapter(22, 2L, 2000)))
                .thenReturn(List.of(chapter(11, 1L, 1000), chapter(12, 1L, 2000)))
                .thenReturn(List.of());

        service.delete(7L, 2L);

        List<LambdaUpdateWrapper<ScribeChapter>> moves = chapterMoves();
        assertEquals(2, moves.size(), "上一卷原有章节排序不变，不应产生无效更新");
        assertTrue(moves.stream().anyMatch(w -> movedTo(w, 21L, 1L, 3000)));
        assertTrue(moves.stream().anyMatch(w -> movedTo(w, 22L, 1L, 4000)));
        verify(volumeMapper).deleteById(2L);
    }

    @Test
    void deleteFirstVolumeMergesIntoNextHead() {
        when(volumeMapper.selectOne(any(Wrapper.class))).thenReturn(volume(1, 1000));
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(volume(1, 1000), volume(2, 2000)));
        when(chapterMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(chapter(11, 1L, 1000)))
                .thenReturn(List.of(chapter(21, 2L, 1000)))
                .thenReturn(List.of());

        service.delete(7L, 1L);

        List<LambdaUpdateWrapper<ScribeChapter>> moves = chapterMoves();
        assertTrue(moves.stream().anyMatch(w -> movedTo(w, 11L, 2L, 1000)), "被删卷的章节排到下一卷最前");
        assertTrue(moves.stream().anyMatch(w -> movedTo(w, 21L, 2L, 2000)), "下一卷原有章节顺延");
    }

    @Test
    void deleteOnlyVolumeReturnsToLooseChapters() {
        when(volumeMapper.selectOne(any(Wrapper.class))).thenReturn(volume(1, 1000));
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(volume(1, 1000)));
        when(chapterMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(chapter(11, 1L, 1000), chapter(12, 1L, 2000)))
                .thenReturn(List.of());

        service.delete(7L, 1L);

        List<LambdaUpdateWrapper<ScribeChapter>> moves = chapterMoves();
        assertEquals(2, moves.size());
        assertTrue(moves.stream().allMatch(w -> w.getParamNameValuePairs().containsValue(null)), "章节 volume_id 应置空");
        verify(volumeMapper).deleteById(1L);
    }

    @Test
    void sortRejectsIncompleteOrForeignTree() {
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(volume(1, 1000), volume(2, 2000)));
        when(chapterMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chapter(11, 1L, 1000), chapter(21, 2L, 1000)));

        TocSortRequest missingChapter = tree(node(2L, 21L), node(1L));
        BizException e = assertThrows(BizException.class, () -> service.sort(7L, missingChapter));
        assertEquals(HttpStatus.CONFLICT, e.getCode());

        TocSortRequest foreignVolume = tree(node(2L, 21L), node(9L, 11L));
        assertThrows(BizException.class, () -> service.sort(7L, foreignVolume));

        TocSortRequest duplicated = tree(node(1L, 11L, 11L), node(2L, 21L));
        assertThrows(BizException.class, () -> service.sort(7L, duplicated));

        TocSortRequest flatOnVolumedWork = new TocSortRequest();
        flatOnVolumedWork.setChapterIds(List.of(11L, 21L));
        assertThrows(BizException.class, () -> service.sort(7L, flatOnVolumedWork));

        verify(chapterMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void sortMovesChapterAcrossVolumesAndReordersVolumes() {
        when(volumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(volume(1, 1000), volume(2, 2000)));
        when(chapterMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(chapter(11, 1L, 1000), chapter(12, 1L, 2000), chapter(21, 2L, 1000)))
                .thenReturn(List.of());

        // 卷二提到前面；章节 12 从卷一末尾移到卷二开头
        service.sort(7L, tree(node(2L, 12L, 21L), node(1L, 11L)));

        List<LambdaUpdateWrapper<ScribeChapter>> moves = chapterMoves();
        assertEquals(2, moves.size(), "11 没动，不应更新");
        assertTrue(moves.stream().anyMatch(w -> movedTo(w, 12L, 2L, 1000)));
        assertTrue(moves.stream().anyMatch(w -> movedTo(w, 21L, 2L, 2000)));
        verify(volumeMapper, org.mockito.Mockito.times(2)).update(isNull(), any(Wrapper.class));
    }

    private static TocSortRequest.VolumeNode node(Long id, Long... chapterIds) {
        TocSortRequest.VolumeNode n = new TocSortRequest.VolumeNode();
        n.setId(id);
        n.setChapterIds(List.of(chapterIds));
        return n;
    }

    private static TocSortRequest tree(TocSortRequest.VolumeNode... nodes) {
        TocSortRequest req = new TocSortRequest();
        req.setVolumes(List.of(nodes));
        return req;
    }
}
