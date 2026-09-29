package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.LoreSaveRequest;
import com.nebula.scribe.entity.ScribeLoreEntry;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.mapper.ScribeLoreEntryMapper;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.service.ScribeWorkGuard;
import com.nebula.scribe.vo.LoreEntryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScribeLoreServiceImplTest {

    private ScribeLoreEntryMapper loreMapper;
    private ScribeWorkMapper workMapper;
    private ScribeLoreServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScribeWork.class);
        TableInfoHelper.initTableInfo(assistant, ScribeLoreEntry.class);
    }

    @BeforeEach
    void setUp() {
        loreMapper = mock(ScribeLoreEntryMapper.class);
        workMapper = mock(ScribeWorkMapper.class);
        service = new ScribeLoreServiceImpl(loreMapper, new ScribeWorkGuard(workMapper));
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        ScribeWork work = new ScribeWork();
        work.setId(7L);
        work.setUserId(42L);
        when(workMapper.selectOne(any(Wrapper.class))).thenReturn(work);
        when(loreMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        doAnswer(inv -> {
            inv.<ScribeLoreEntry>getArgument(0).setId(100L);
            return 1;
        }).when(loreMapper).insert(any(ScribeLoreEntry.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static LoreSaveRequest request(String kind, String name) {
        LoreSaveRequest req = new LoreSaveRequest();
        req.setKind(kind);
        req.setName(name);
        return req;
    }

    private static ScribeLoreEntry entry(long id, String name) {
        ScribeLoreEntry e = new ScribeLoreEntry();
        e.setId(id);
        e.setWorkId(7L);
        e.setKind("character");
        e.setName(name);
        e.setSummary("旧概述");
        e.setDetail("## 外貌\n瘦高");
        e.setTags("[\"主角\"]");
        e.setPinned(true);
        return e;
    }

    @Test
    void createTrimsCleansAndDefaultsToUnpinned() {
        LoreSaveRequest req = request("character", "  沈砚 ");
        req.setAliases(List.of("断刀捕快", " 断刀捕快 ", "沈砚", ""));
        req.setSummary(" ");
        req.setTags(List.of("主角"));

        LoreEntryVO vo = service.create(7L, req);

        ArgumentCaptor<ScribeLoreEntry> captor = ArgumentCaptor.forClass(ScribeLoreEntry.class);
        verify(loreMapper).insert(captor.capture());
        ScribeLoreEntry saved = captor.getValue();
        assertEquals(7L, saved.getWorkId());
        assertEquals("沈砚", saved.getName());
        assertEquals("[\"断刀捕快\"]", saved.getAliases(), "别名应去空白、去重，并去掉与名称相同的");
        assertNull(saved.getSummary(), "空白概述应存 null");
        assertEquals(Boolean.FALSE, saved.getPinned());

        assertEquals(100L, vo.getId());
        assertEquals(List.of("断刀捕快"), vo.getAliases());
        assertEquals(List.of("主角"), vo.getTags());
        assertFalse(vo.getPinned());
    }

    @Test
    void createRejectsUnknownKind() {
        BizException e = assertThrows(BizException.class, () -> service.create(7L, request("lore", "鼓楼")));

        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(loreMapper, never()).insert(any(ScribeLoreEntry.class));
    }

    @Test
    void createRejectsNameTakenInSameWork() {
        when(loreMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.create(7L, request("location", "沈砚")));

        assertEquals(HttpStatus.CONFLICT, e.getCode());
        verify(loreMapper, never()).insert(any(ScribeLoreEntry.class));
    }

    @Test
    void createInOthersWorkIsNotFound() {
        when(workMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.create(8L, request("character", "沈砚")));

        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
        verify(loreMapper, never()).insert(any(ScribeLoreEntry.class));
    }

    @Test
    void listSkipsDetailPutsPinnedFirstAndIgnoresUnknownKind() {
        ScribeLoreEntry row = entry(1, "沈砚");
        row.setDetail(null);
        when(loreMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

        List<LoreEntryVO> rows = service.list(7L, "unknown");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ScribeLoreEntry>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(loreMapper).selectList(captor.capture());
        LambdaQueryWrapper<ScribeLoreEntry> wrapper = (LambdaQueryWrapper<ScribeLoreEntry>) captor.getValue();
        assertFalse(wrapper.getSqlSelect().contains("detail"), wrapper.getSqlSelect());
        String segment = wrapper.getSqlSegment();
        assertFalse(segment.contains("kind"), "非法类型应按不筛选处理: " + segment);
        assertTrue(segment.indexOf("pinned") < segment.indexOf("update_time"), "固定的应排在前面: " + segment);

        assertEquals(1, rows.size());
        assertNull(rows.getFirst().getDetail());
        assertTrue(rows.getFirst().getPinned());
    }

    @Test
    void listFiltersByValidKind() {
        when(loreMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.list(7L, "faction");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ScribeLoreEntry>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(loreMapper).selectList(captor.capture());
        LambdaQueryWrapper<ScribeLoreEntry> wrapper = (LambdaQueryWrapper<ScribeLoreEntry>) captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("kind"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("faction"));
    }

    @Test
    void detailOfEntryOutsideWorkIsNotFound() {
        when(loreMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.detail(7L, 99L));

        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void updateOverwritesWholeFormAndClearsOptionalFields() {
        when(loreMapper.selectOne(any(Wrapper.class))).thenReturn(entry(1, "沈砚"));
        when(loreMapper.selectById(1L)).thenReturn(entry(1, "沈砚"));
        LoreSaveRequest req = request("character", "沈砚");

        LoreEntryVO vo = service.update(7L, 1L, req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ScribeLoreEntry>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(loreMapper).update(any(ScribeLoreEntry.class), captor.capture());
        LambdaUpdateWrapper<ScribeLoreEntry> wrapper = (LambdaUpdateWrapper<ScribeLoreEntry>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        for (String column : List.of("kind=", "name=", "aliases=", "summary=", "detail=", "tags=", "pinned=")) {
            assertTrue(sqlSet.contains(column), "整表单覆盖应写 " + column + ": " + sqlSet);
        }
        assertTrue(wrapper.getParamNameValuePairs().containsValue(Boolean.FALSE), "未传 pinned 应写成否");
        assertTrue(wrapper.getSqlSegment().contains("work_id"), "更新应限定在所属作品内");
        assertNotNull(vo);
    }

    @Test
    void updateExcludesSelfFromNameCheck() {
        when(loreMapper.selectOne(any(Wrapper.class))).thenReturn(entry(1, "沈砚"));
        when(loreMapper.selectById(1L)).thenReturn(entry(1, "沈砚"));

        service.update(7L, 1L, request("character", "沈砚"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ScribeLoreEntry>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(loreMapper).selectCount(captor.capture());
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("id <>"), "查重应排除自身: " + segment);
    }

    @Test
    void updateRejectsNameTakenByAnotherEntry() {
        when(loreMapper.selectOne(any(Wrapper.class))).thenReturn(entry(1, "沈砚"));
        when(loreMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BizException e = assertThrows(BizException.class,
                () -> service.update(7L, 1L, request("character", "柳三娘")));

        assertEquals(HttpStatus.CONFLICT, e.getCode());
        verify(loreMapper, never()).update(any(ScribeLoreEntry.class), any(Wrapper.class));
    }

    @Test
    void deleteStampsDeleteTimeThenSoftDeletes() {
        when(loreMapper.selectOne(any(Wrapper.class))).thenReturn(entry(1, "沈砚"));

        service.delete(7L, 1L);

        InOrder order = inOrder(loreMapper);
        ArgumentCaptor<ScribeLoreEntry> captor = ArgumentCaptor.forClass(ScribeLoreEntry.class);
        order.verify(loreMapper).updateById(captor.capture());
        order.verify(loreMapper).deleteById(1L);
        assertNotNull(captor.getValue().getDeleteTime());
    }
}
