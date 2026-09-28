package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.NoteSaveRequest;
import com.nebula.space.entity.SpaceNote;
import com.nebula.space.mapper.SpaceNoteMapper;
import com.nebula.space.vo.me.NoteStatsVO;
import com.nebula.space.vo.me.NoteVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceNoteServiceImplTest {

    private SpaceNoteMapper noteMapper;
    private SpaceNoteServiceImpl service;
    private final LocalDate today = LocalDate.now();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceNote.class);
    }

    @BeforeEach
    void setUp() {
        noteMapper = mock(SpaceNoteMapper.class);
        service = new SpaceNoteServiceImpl(noteMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static NoteSaveRequest req() {
        return new NoteSaveRequest();
    }

    private SpaceNote given(boolean pinned, boolean archived, LocalDate expire, String tags) {
        SpaceNote n = new SpaceNote();
        n.setId(5L);
        n.setUserId(42L);
        n.setContent("旧");
        n.setColor("plain");
        n.setPinned(pinned ? 1 : 0);
        n.setArchived(archived ? 1 : 0);
        n.setExpireDate(expire);
        n.setTags(tags);
        when(noteMapper.selectOne(any(Wrapper.class))).thenReturn(n);
        return n;
    }

    @Test
    void createIsTemporaryWithDefaultTtl() {
        NoteSaveRequest r = req();
        r.setContent("取件码");
        NoteVO vo = service.create(r);

        ArgumentCaptor<SpaceNote> saved = ArgumentCaptor.forClass(SpaceNote.class);
        verify(noteMapper).insert(saved.capture());
        assertEquals(42L, saved.getValue().getUserId());
        assertFalse(vo.getPinned());
        assertEquals(today.plusDays(7), vo.getExpireDate());
        assertEquals("plain", vo.getColor());
        assertEquals(List.of(), vo.getTags());
    }

    @Test
    void createHonorsTtlPreference() {
        NoteSaveRequest r = req();
        r.setTtlDays(3);
        assertEquals(today.plusDays(3), service.create(r).getExpireDate());
    }

    @Test
    void createWithTagsOrLongDefaultIsPinned() {
        NoteSaveRequest tagged = req();
        tagged.setTags(List.of(" 工作 ", "工作", ""));
        NoteVO a = service.create(tagged);
        assertTrue(a.getPinned());
        assertNull(a.getExpireDate());
        assertEquals(List.of("工作"), a.getTags());

        NoteSaveRequest longDefault = req();
        longDefault.setTtlDays(0);
        assertTrue(service.create(longDefault).getPinned());
    }

    @Test
    void editingTemporaryNoteRenewsExpiry() {
        given(false, false, today.plusDays(1), "[]");
        NoteSaveRequest r = req();
        r.setContent("新");
        NoteVO vo = service.update(5L, r);
        assertEquals(today.plusDays(7), vo.getExpireDate());
        assertEquals("新", vo.getContent());
    }

    @Test
    void colorChangeDoesNotRenewExpiry() {
        given(false, false, today.plusDays(1), "[]");
        NoteSaveRequest r = req();
        r.setColor("blue");
        assertEquals(today.plusDays(1), service.update(5L, r).getExpireDate());
    }

    @Test
    void pinClearsExpiryAndUnpinRestartsIt() {
        given(false, false, today.plusDays(2), "[]");
        NoteSaveRequest pin = req();
        pin.setPinned(true);
        assertNull(service.update(5L, pin).getExpireDate());

        given(true, false, null, "[]");
        NoteSaveRequest unpin = req();
        unpin.setPinned(false);
        unpin.setTtlDays(0);
        NoteVO vo = service.update(5L, unpin);
        assertFalse(vo.getPinned());
        assertEquals(today.plusDays(7), vo.getExpireDate());
    }

    @Test
    void firstTagMakesNoteLongTerm() {
        given(false, false, today.plusDays(2), "[]");
        NoteSaveRequest r = req();
        r.setTags(List.of("学习"));
        NoteVO vo = service.update(5L, r);
        assertTrue(vo.getPinned());
        assertNull(vo.getExpireDate());
    }

    @Test
    void restoreFromArchiveBecomesTemporary() {
        given(true, true, null, "[\"生活\"]");
        NoteSaveRequest r = req();
        r.setArchived(false);
        NoteVO vo = service.update(5L, r);
        assertFalse(vo.getArchived());
        assertFalse(vo.getPinned());
        assertEquals(today.plusDays(7), vo.getExpireDate());
    }

    @Test
    void editKeepsLaterManualExpiry() {
        given(false, false, today.plusDays(30), "[]");
        NoteSaveRequest r = req();
        r.setContent("新");
        assertEquals(today.plusDays(30), service.update(5L, r).getExpireDate());
    }

    @Test
    void manualExpiryMakesPinnedNoteTemporary() {
        given(true, false, null, "[\"工作\"]");
        NoteSaveRequest r = req();
        r.setExpireDate(today.plusDays(3));
        NoteVO vo = service.update(5L, r);
        assertFalse(vo.getPinned());
        assertEquals(today.plusDays(3), vo.getExpireDate());
    }

    @Test
    void createWithManualExpiryIsTemporaryEvenWithTags() {
        NoteSaveRequest r = req();
        r.setTags(List.of("工作"));
        r.setExpireDate(today.plusDays(14));
        NoteVO vo = service.create(r);
        assertFalse(vo.getPinned());
        assertEquals(today.plusDays(14), vo.getExpireDate());
    }

    @Test
    void missingNoteIs404() {
        when(noteMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.detail(9L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void statsCountsLiveNotesAndTags() {
        SpaceNote temp = note(0, 0, today.plusDays(1), "[]");
        SpaceNote later = note(0, 0, today.plusDays(5), "[\"工作\"]");
        SpaceNote pinned = note(1, 0, null, "[\"工作\",\"学习\"]");
        SpaceNote archived = note(0, 1, null, "[\"生活\"]");
        when(noteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(temp, later, pinned, archived));

        NoteStatsVO s = service.stats();
        assertEquals(3, s.getAll());
        assertEquals(2, s.getTemporary());
        assertEquals(1, s.getPinned());
        assertEquals(1, s.getArchived());
        assertEquals(1, s.getDueTomorrow());
        assertEquals("工作", s.getTags().get(0).getName());
        assertEquals(2, s.getTags().get(0).getCount());
        assertEquals(2, s.getTags().size());
    }

    private static SpaceNote note(int pinned, int archived, LocalDate expire, String tags) {
        SpaceNote n = new SpaceNote();
        n.setPinned(pinned);
        n.setArchived(archived);
        n.setExpireDate(expire);
        n.setTags(tags);
        return n;
    }
}
