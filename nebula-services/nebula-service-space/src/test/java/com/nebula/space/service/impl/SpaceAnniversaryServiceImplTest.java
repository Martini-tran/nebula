package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.AnniversarySaveRequest;
import com.nebula.space.entity.SpaceAnniversary;
import com.nebula.space.mapper.SpaceAnniversaryMapper;
import com.nebula.space.vo.me.AnniversaryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceAnniversaryServiceImplTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpaceAnniversaryMapper annivMapper;
    private SpaceAnniversaryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceAnniversary.class);
    }

    @BeforeEach
    void setUp() {
        annivMapper = mock(SpaceAnniversaryMapper.class);
        service = new SpaceAnniversaryServiceImpl(annivMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static AnniversarySaveRequest body(String json) throws Exception {
        return JSON.readValue(json, AnniversarySaveRequest.class);
    }

    private SpaceAnniversary given() {
        SpaceAnniversary a = new SpaceAnniversary();
        a.setId(5L);
        a.setUserId(42L);
        a.setTitle("租约到期");
        a.setIcon("lucide:house");
        a.setAnnivType("countdown");
        a.setAnnivDate(LocalDate.of(2027, 3, 19));
        a.setCalendar("solar");
        a.setRemindDays(30);
        a.setCreateTask(1);
        a.setTaskTitle("和房东谈续租");
        a.setTaskFor(LocalDate.of(2027, 3, 19));
        a.setNote("");
        a.setTag("合同");
        when(annivMapper.selectOne(any(Wrapper.class))).thenReturn(a);
        return a;
    }

    @Test
    void createLunarBirthday() throws Exception {
        AnniversaryVO vo = service.create(body("{\"title\":\"妈妈生日\",\"icon\":\"lucide:cake\",\"type\":\"annual\",\"date\":\"1962-10-09\","
                + "\"calendar\":\"lunar\",\"lunarMonth\":8,\"lunarDay\":29,\"remindDays\":7,\"createTask\":true,\"taskTitle\":\"买生日礼物\"}"));
        assertEquals("lunar", vo.getCalendar());
        assertEquals(8, vo.getLunarMonth());
        assertEquals(29, vo.getLunarDay());
        assertTrue(vo.getCreateTask());
    }

    @Test
    void lunarNeedsMonthAndDay() {
        BizException e = assertThrows(BizException.class, () -> service.create(body("{\"title\":\"x\",\"type\":\"annual\",\"date\":\"2000-01-01\",\"calendar\":\"lunar\"}")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void countdownCannotBeLunar() throws Exception {
        AnniversaryVO vo = service.create(body("{\"title\":\"x\",\"type\":\"countdown\",\"date\":\"2027-01-01\",\"calendar\":\"lunar\",\"lunarMonth\":1,\"lunarDay\":1}"));
        assertEquals("solar", vo.getCalendar());
        assertNull(vo.getLunarMonth());
    }

    @Test
    void clearingReminderDropsTaskAndTaskFor() throws Exception {
        given();
        AnniversaryVO vo = service.update(5L, body("{\"remindDays\":null,\"taskFor\":null}"));
        assertNull(vo.getRemindDays());
        assertFalse(vo.getCreateTask());
        assertNull(vo.getTaskFor());
        assertEquals("和房东谈续租", vo.getTaskTitle());
    }

    @Test
    void markTaskForOnly() throws Exception {
        given().setTaskFor(null);
        AnniversaryVO vo = service.update(5L, body("{\"taskFor\":\"2027-03-19\"}"));
        assertEquals(LocalDate.of(2027, 3, 19), vo.getTaskFor());
        assertEquals(30, vo.getRemindDays());
        assertTrue(vo.getCreateTask());
    }
}
