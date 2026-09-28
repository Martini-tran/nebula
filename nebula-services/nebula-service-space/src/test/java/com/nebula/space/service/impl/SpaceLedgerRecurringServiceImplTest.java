package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.LedgerRecurringSaveRequest;
import com.nebula.space.entity.SpaceLedgerCategory;
import com.nebula.space.entity.SpaceLedgerEntry;
import com.nebula.space.entity.SpaceLedgerRecurring;
import com.nebula.space.mapper.SpaceLedgerCategoryMapper;
import com.nebula.space.mapper.SpaceLedgerEntryMapper;
import com.nebula.space.mapper.SpaceLedgerRecurringMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceLedgerRecurringServiceImplTest {

    private SpaceLedgerRecurringMapper recurringMapper;
    private SpaceLedgerEntryMapper entryMapper;
    private SpaceLedgerCategoryMapper categoryMapper;
    private SpaceLedgerRecurringServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerRecurring.class);
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerEntry.class);
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerCategory.class);
    }

    @BeforeEach
    void setUp() {
        recurringMapper = mock(SpaceLedgerRecurringMapper.class);
        entryMapper = mock(SpaceLedgerEntryMapper.class);
        categoryMapper = mock(SpaceLedgerCategoryMapper.class);
        service = new SpaceLedgerRecurringServiceImpl(recurringMapper, entryMapper, categoryMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static SpaceLedgerRecurring rent(String start, String filled, int active) {
        SpaceLedgerRecurring r = new SpaceLedgerRecurring();
        r.setId(7L);
        r.setUserId(42L);
        r.setNote("房租");
        r.setAmount(350000L);
        r.setDirection("out");
        r.setCategoryId(3L);
        r.setDayOfMonth(20);
        r.setActive(active);
        r.setStartMonth(start);
        r.setFilledThrough(filled);
        return r;
    }

    private void givenCategory(String kind) {
        SpaceLedgerCategory c = new SpaceLedgerCategory();
        c.setId(3L);
        c.setUserId(42L);
        c.setKind(kind);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(c);
    }

    @Test
    void monthsToFillStopsBeforeDayHasCome() {
        LocalDate today = LocalDate.of(2026, 9, 28);
        assertEquals(List.of(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
                SpaceLedgerRecurringServiceImpl.monthsToFill("2026-07", null, 20, today));
        // 每月 28 号：今天正好 28 号，本月这笔算到了
        assertEquals(List.of(YearMonth.of(2026, 9)), SpaceLedgerRecurringServiceImpl.monthsToFill("2026-09", null, 28, today));
        assertTrue(SpaceLedgerRecurringServiceImpl.monthsToFill("2026-09", "2026-09", 20, today).isEmpty());
        assertTrue(SpaceLedgerRecurringServiceImpl.monthsToFill("2026-10", null, 1, today).isEmpty());
    }

    @Test
    void filledThroughSkipsDeletedMonths() {
        // 已生成到 8 月：哪怕 8 月那笔被删了，也只补 9 月
        assertEquals(List.of(YearMonth.of(2026, 9)),
                SpaceLedgerRecurringServiceImpl.monthsToFill("2026-01", "2026-08", 20, LocalDate.of(2026, 9, 28)));
    }

    @Test
    void fillInsertsOnlyWhenClaimSucceeds() {
        String lastMonth = YearMonth.now().minusMonths(1).toString();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rent(lastMonth, null, 1)));
        when(recurringMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.fill(42L);
        ArgumentCaptor<SpaceLedgerEntry> saved = ArgumentCaptor.forClass(SpaceLedgerEntry.class);
        verify(entryMapper, atLeastOnce()).insert(saved.capture());
        SpaceLedgerEntry first = saved.getAllValues().get(0);
        assertEquals(7L, first.getRecurringId());
        assertEquals(YearMonth.parse(lastMonth).atDay(20), first.getEntryDate());
        assertEquals(350000L, first.getAmount());
    }

    @Test
    void fillSkipsWhenAnotherRequestClaimedFirst() {
        String lastMonth = YearMonth.now().minusMonths(1).toString();
        when(recurringMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rent(lastMonth, null, 1)));
        when(recurringMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        service.fill(42L);
        verify(entryMapper, never()).insert(any(SpaceLedgerEntry.class));
    }

    @Test
    void resumeSkipsPausedMonths() {
        givenCategory("out");
        SpaceLedgerRecurring r = rent("2026-01", "2026-03", 0);
        when(recurringMapper.selectOne(any(Wrapper.class))).thenReturn(r);
        LedgerRecurringSaveRequest req = new LedgerRecurringSaveRequest();
        req.setActive(true);
        service.update(7L, req);
        assertEquals(YearMonth.now().minusMonths(1).toString(), r.getFilledThrough());
        assertEquals(1, r.getActive());
        verify(recurringMapper, times(1)).updateById(r);
    }

    @Test
    void categoryMustMatchDirection() {
        givenCategory("in");
        LedgerRecurringSaveRequest req = new LedgerRecurringSaveRequest();
        req.setNote("房租");
        req.setAmount(350000L);
        req.setDirection("out");
        req.setCategoryId(3L);
        req.setDay(20);
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(recurringMapper, never()).insert(any(SpaceLedgerRecurring.class));
    }
}
