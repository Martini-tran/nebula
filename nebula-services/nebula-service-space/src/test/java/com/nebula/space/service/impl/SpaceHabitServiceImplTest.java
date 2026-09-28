package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.HabitFreq;
import com.nebula.space.dto.me.HabitLogSetRequest;
import com.nebula.space.dto.me.HabitSaveRequest;
import com.nebula.space.entity.SpaceHabit;
import com.nebula.space.entity.SpaceHabitLog;
import com.nebula.space.mapper.SpaceHabitLogMapper;
import com.nebula.space.mapper.SpaceHabitMapper;
import com.nebula.space.vo.me.HabitLogVO;
import com.nebula.space.vo.me.HabitVO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceHabitServiceImplTest {

    private SpaceHabitMapper habitMapper;
    private SpaceHabitLogMapper logMapper;
    private SpaceHabitServiceImpl service;
    private final LocalDate today = LocalDate.now();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceHabit.class);
        TableInfoHelper.initTableInfo(assistant, SpaceHabitLog.class);
    }

    @BeforeEach
    void setUp() {
        habitMapper = mock(SpaceHabitMapper.class);
        logMapper = mock(SpaceHabitLogMapper.class);
        service = new SpaceHabitServiceImpl(habitMapper, logMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void givenHabit() {
        SpaceHabit h = new SpaceHabit();
        h.setId(7L);
        h.setUserId(42L);
        h.setName("喝水");
        h.setFreq("{\"type\":\"daily\"}");
        h.setReminders("[]");
        when(habitMapper.selectOne(any(Wrapper.class))).thenReturn(h);
    }

    private SpaceHabitLog givenLog(LocalDate date, int value, String note) {
        SpaceHabitLog log = new SpaceHabitLog();
        log.setId(99L);
        log.setUserId(42L);
        log.setHabitId(7L);
        log.setLogDate(date);
        log.setValue(value);
        log.setNote(note);
        log.setBackfilled(0);
        when(logMapper.selectOne(any(Wrapper.class))).thenReturn(log);
        return log;
    }

    private static HabitLogSetRequest set(int value, String note) {
        HabitLogSetRequest r = new HabitLogSetRequest();
        r.setValue(value);
        r.setNote(note);
        return r;
    }

    @Test
    void createAppliesDefaults() {
        HabitSaveRequest r = new HabitSaveRequest();
        r.setName(" 早睡 ");
        when(habitMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        HabitVO vo = service.create(r);
        assertEquals("早睡", vo.getName());
        assertEquals("check", vo.getKind());
        assertEquals(1, vo.getTarget());
        assertEquals("daily", vo.getFreq().getType());
        assertEquals(List.of(), vo.getReminders());
        assertEquals(3, vo.getSortOrder());
        assertFalse(vo.getArchived());
    }

    @Test
    void freqKeepsOnlyRelevantFieldsAndValidates() {
        HabitSaveRequest r = new HabitSaveRequest();
        r.setName("力量训练");
        HabitFreq freq = new HabitFreq();
        freq.setType("weekdays");
        freq.setN(3);
        freq.setDays(List.of(5, 1, 3, 1));
        r.setFreq(freq);
        when(habitMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        HabitVO vo = service.create(r);
        assertNull(vo.getFreq().getN());
        assertEquals(List.of(1, 3, 5), vo.getFreq().getDays());

        HabitSaveRequest bad = new HabitSaveRequest();
        bad.setName("x");
        HabitFreq weekly = new HabitFreq();
        weekly.setType("weekly_n");
        bad.setFreq(weekly);
        assertThrows(BizException.class, () -> service.create(bad));
    }

    @Test
    void firstLogTodayInsertsNotBackfilled() {
        givenHabit();
        HabitLogVO vo = service.setLog(7L, today, set(5, null));
        ArgumentCaptor<SpaceHabitLog> saved = ArgumentCaptor.forClass(SpaceHabitLog.class);
        verify(logMapper).insert(saved.capture());
        assertEquals(42L, saved.getValue().getUserId());
        assertEquals(5, vo.getValue());
        assertEquals("", vo.getNote());
        assertFalse(vo.getBackfilled());
    }

    @Test
    void backfillMarkedAndLimited() {
        givenHabit();
        assertTrue(service.setLog(7L, today.minusDays(2), set(1, null)).getBackfilled());

        BizException tooOld = assertThrows(BizException.class, () -> service.setLog(7L, today.minusDays(3), set(1, null)));
        assertEquals(HttpStatus.BAD_REQUEST, tooOld.getCode());
        assertThrows(BizException.class, () -> service.setLog(7L, today.plusDays(1), set(1, null)));
    }

    @Test
    void overwriteKeepsNoteWhenNotSent() {
        givenHabit();
        givenLog(today, 3, "早上两杯");
        HabitLogVO vo = service.setLog(7L, today, set(6, null));
        assertEquals(6, vo.getValue());
        assertEquals("早上两杯", vo.getNote());
        verify(logMapper).updateById(any(SpaceHabitLog.class));
        verify(logMapper, never()).insert(any(SpaceHabitLog.class));
    }

    @Test
    void zeroDeletesTheDay() {
        givenHabit();
        givenLog(today, 1, "");
        assertNull(service.setLog(7L, today, set(0, null)));
        verify(logMapper).deleteById(99L);
    }

    @Test
    void logOnOthersHabitIs404() {
        when(habitMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.setLog(7L, today, set(1, null)));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }
}
