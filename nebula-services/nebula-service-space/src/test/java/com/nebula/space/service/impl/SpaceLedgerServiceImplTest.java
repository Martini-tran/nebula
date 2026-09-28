package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.LedgerBudgetSaveRequest;
import com.nebula.space.dto.me.LedgerEntrySaveRequest;
import com.nebula.space.entity.SpaceLedgerBudget;
import com.nebula.space.entity.SpaceLedgerCategory;
import com.nebula.space.entity.SpaceLedgerEntry;
import com.nebula.space.entity.SpaceLedgerRecurring;
import com.nebula.space.mapper.SpaceLedgerBudgetMapper;
import com.nebula.space.mapper.SpaceLedgerCategoryMapper;
import com.nebula.space.mapper.SpaceLedgerEntryMapper;
import com.nebula.space.mapper.SpaceLedgerRecurringMapper;
import com.nebula.space.service.SpaceLedgerRecurringService;
import com.nebula.space.vo.me.LedgerBudgetVO;
import com.nebula.space.vo.me.LedgerEntryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceLedgerServiceImplTest {

    private SpaceLedgerCategoryMapper categoryMapper;
    private SpaceLedgerEntryMapper entryMapper;
    private SpaceLedgerRecurringMapper recurringMapper;
    private SpaceLedgerBudgetMapper budgetMapper;
    private SpaceLedgerRecurringService recurringService;
    private SpaceLedgerServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerCategory.class);
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerEntry.class);
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerRecurring.class);
        TableInfoHelper.initTableInfo(assistant, SpaceLedgerBudget.class);
    }

    @BeforeEach
    void setUp() {
        categoryMapper = mock(SpaceLedgerCategoryMapper.class);
        entryMapper = mock(SpaceLedgerEntryMapper.class);
        recurringMapper = mock(SpaceLedgerRecurringMapper.class);
        budgetMapper = mock(SpaceLedgerBudgetMapper.class);
        recurringService = mock(SpaceLedgerRecurringService.class);
        service = new SpaceLedgerServiceImpl(categoryMapper, entryMapper, recurringMapper, budgetMapper, recurringService);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void givenCategory(String kind) {
        SpaceLedgerCategory c = new SpaceLedgerCategory();
        c.setId(1L);
        c.setUserId(42L);
        c.setKind(kind);
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(c);
    }

    private static LedgerEntrySaveRequest lunch() {
        LedgerEntrySaveRequest req = new LedgerEntrySaveRequest();
        req.setAmount(3200L);
        req.setDirection("out");
        req.setCategoryId(1L);
        req.setDate(LocalDate.of(2026, 9, 28));
        req.setNote(" 午饭 ");
        return req;
    }

    @Test
    void firstReadSeedsDefaultCategories() {
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(new SpaceLedgerCategory()));
        service.listCategories();
        ArgumentCaptor<SpaceLedgerCategory> saved = ArgumentCaptor.forClass(SpaceLedgerCategory.class);
        verify(categoryMapper, times(10)).insert(saved.capture());
        SpaceLedgerCategory food = saved.getAllValues().get(0);
        assertEquals(42L, food.getUserId());
        assertEquals("餐饮", food.getName());
        assertEquals("lucide:utensils", food.getIcon());
        assertTrue(food.getKeywords().contains("外卖"));
    }

    @Test
    void createEntryOutputsAmountAsNumber() {
        givenCategory("out");
        LedgerEntryVO vo = service.createEntry(lunch());
        assertEquals(new BigDecimal(3200), vo.getAmount());
        assertEquals("午饭", vo.getNote());
        assertNull(vo.getRecurringId());
    }

    @Test
    void expenseCannotUseIncomeCategory() {
        givenCategory("in");
        BizException e = assertThrows(BizException.class, () -> service.createEntry(lunch()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void foreignRecurringIdIsDropped() {
        givenCategory("out");
        when(recurringMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        LedgerEntrySaveRequest req = lunch();
        req.setRecurringId(99L);
        assertNull(service.createEntry(req).getRecurringId());
    }

    @Test
    void listEntriesFillsRecurringFirst() {
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.listEntries(null);
        verify(recurringService).fill(42L);
    }

    @Test
    void budgetInheritsEarlierMonth() {
        SpaceLedgerBudget b = new SpaceLedgerBudget();
        b.setBudgetMonth("2026-01");
        b.setTotal(800000L);
        b.setItems("{\"1\":150000}");
        when(budgetMapper.selectOne(any(Wrapper.class))).thenReturn(b);
        LedgerBudgetVO vo = service.getBudget("2026-09");
        assertEquals("2026-09", vo.getMonth());
        assertEquals(new BigDecimal(800000), vo.getTotal());
        assertEquals(new BigDecimal(150000), vo.getItems().get("1"));
    }

    @Test
    void noBudgetIsEmpty() {
        LedgerBudgetVO vo = service.getBudget("2026-09");
        assertNull(vo.getTotal());
        assertTrue(vo.getItems().isEmpty());
    }

    @Test
    void saveBudgetDropsZeroItems() {
        LedgerBudgetSaveRequest req = new LedgerBudgetSaveRequest();
        req.setItems(Map.of("1", 150000L, "2", 0L));
        LedgerBudgetVO vo = service.saveBudget("2026-09", req);
        assertEquals(1, vo.getItems().size());
        assertNull(vo.getTotal());
    }

    @Test
    void badMonthIs400() {
        BizException e = assertThrows(BizException.class, () -> service.getBudget("2026-13"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }
}
