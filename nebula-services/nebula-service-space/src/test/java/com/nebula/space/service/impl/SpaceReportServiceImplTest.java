package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.ReportSaveRequest;
import com.nebula.space.entity.SpaceReport;
import com.nebula.space.mapper.SpaceReportMapper;
import com.nebula.space.vo.me.ReportVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceReportServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 27);

    private SpaceReportMapper reportMapper;
    private SpaceReportServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceReport.class);
    }

    @BeforeEach
    void setUp() {
        reportMapper = mock(SpaceReportMapper.class);
        service = new SpaceReportServiceImpl(reportMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static ReportSaveRequest body(String content) {
        ReportSaveRequest req = new ReportSaveRequest();
        req.setContent(content);
        return req;
    }

    private SpaceReport given() {
        SpaceReport r = new SpaceReport();
        r.setId(5L);
        r.setUserId(42L);
        r.setReportType("day");
        r.setPeriodStart(DAY);
        r.setContent("- 旧内容");
        when(reportMapper.selectOne(any(Wrapper.class))).thenReturn(r);
        return r;
    }

    @Test
    void firstSaveInsertsForCurrentUser() {
        ReportVO vo = service.save("day", DAY, body("- 写完日报接口"));
        ArgumentCaptor<SpaceReport> saved = ArgumentCaptor.forClass(SpaceReport.class);
        verify(reportMapper).insert(saved.capture());
        assertEquals(42L, saved.getValue().getUserId());
        assertEquals("day", vo.getType());
        assertEquals(DAY, vo.getDate());
        assertEquals("- 写完日报接口", vo.getContent());
    }

    @Test
    void saveOverwritesExisting() {
        given();
        ReportVO vo = service.save("day", DAY, body("- 新内容"));
        verify(reportMapper, never()).insert(any(SpaceReport.class));
        verify(reportMapper).updateById(any(SpaceReport.class));
        assertEquals(5L, vo.getId());
        assertEquals("- 新内容", vo.getContent());
    }

    @Test
    void blankContentDeletes() {
        given();
        assertNull(service.save("day", DAY, body("  \n")));
        verify(reportMapper).deleteById(5L);
    }

    @Test
    void concurrentFirstSaveFallsBackToUpdate() {
        SpaceReport other = new SpaceReport();
        other.setId(8L);
        other.setReportType("week");
        other.setPeriodStart(DAY);
        when(reportMapper.selectOne(any(Wrapper.class))).thenReturn(null, other);
        when(reportMapper.insert(any(SpaceReport.class))).thenThrow(new DuplicateKeyException("dup"));
        ReportVO vo = service.save("week", DAY, body("本周"));
        assertEquals(8L, vo.getId());
        verify(reportMapper).updateById(other);
    }

    @Test
    void unknownTypeIs400() {
        BizException e = assertThrows(BizException.class, () -> service.get("month", DAY));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void missingReportIsNull() {
        assertNull(service.get("week", DAY));
    }
}
