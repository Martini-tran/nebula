package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.MeetingSaveRequest;
import com.nebula.space.entity.SpaceMeeting;
import com.nebula.space.mapper.SpaceMeetingMapper;
import com.nebula.space.vo.me.MeetingVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceMeetingServiceImplTest {

    // 与运行时一致：Spring Boot 4 的 MVC 用 Jackson 3，内置 java.time 支持
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpaceMeetingMapper meetingMapper;
    private SpaceMeetingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceMeeting.class);
    }

    @BeforeEach
    void setUp() {
        meetingMapper = mock(SpaceMeetingMapper.class);
        service = new SpaceMeetingServiceImpl(meetingMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static MeetingSaveRequest body(String json) throws Exception {
        return JSON.readValue(json, MeetingSaveRequest.class);
    }

    private SpaceMeeting given() {
        SpaceMeeting m = new SpaceMeeting();
        m.setId(3L);
        m.setUserId(42L);
        m.setTitle("产品周会");
        m.setMeetingDate(LocalDate.of(2026, 9, 28));
        m.setStartTime("10:00");
        m.setDurationMin(45);
        m.setTemplate("weekly");
        m.setAttendees("[{\"name\":\"我\",\"me\":true},{\"name\":\"张工\"}]");
        m.setAgenda("[{\"id\":\"a1\",\"title\":\"上周回顾\",\"budgetMin\":15,\"usedSec\":0}]");
        m.setContent("");
        m.setStatus("live");
        m.setCurrentAgendaId("a1");
        m.setStartedAt(LocalDateTime.of(2026, 9, 28, 10, 1));
        m.setSyncedTasks("{}");
        when(meetingMapper.selectOne(any(Wrapper.class))).thenReturn(m);
        return m;
    }

    @Test
    void createDefaultsToMeAndPlanned() throws Exception {
        MeetingVO vo = service.create(body("{\"title\":\"1:1\",\"date\":\"2026-09-29\",\"startTime\":\"16:00\"}"));
        assertEquals("planned", vo.getStatus());
        assertEquals(30, vo.getDurationMin());
        assertEquals(1, vo.getAttendees().size());
        assertTrue(vo.getAttendees().get(0).getMe());
        assertEquals(0, vo.getAgenda().size());
        assertEquals(0, vo.getSyncedTasks().size());
    }

    @Test
    void createRequiresDateAndTime() {
        BizException e = assertThrows(BizException.class, () -> service.create(body("{\"title\":\"x\"}")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void endMeetingParsesFrontendStampAndClearsCurrentAgenda() throws Exception {
        given();
        MeetingVO vo = service.update(3L, body("{\"status\":\"done\",\"endedAt\":\"2026-09-28 10:44:05\",\"currentAgendaId\":null}"));
        assertEquals("done", vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 28, 10, 44, 5), vo.getEndedAt());
        assertNull(vo.getCurrentAgendaId());
        // 没传的字段不动
        assertEquals("weekly", vo.getTemplate());
        assertEquals(LocalDateTime.of(2026, 9, 28, 10, 1), vo.getStartedAt());
    }

    @Test
    void syncedTasksKeepTaskIdType() throws Exception {
        given();
        MeetingVO vo = service.update(3L, body("{\"syncedTasks\":{\"看方案\":12}}"));
        assertEquals(12, vo.getSyncedTasks().get("看方案"));
    }

    @Test
    void stampAcceptsIsoAndRejectsGarbage() {
        assertEquals(LocalDateTime.of(2026, 9, 28, 9, 0), SpaceMeetingServiceImpl.parseStamp("2026-09-28T09:00:00"));
        assertNull(SpaceMeetingServiceImpl.parseStamp(""));
        assertThrows(BizException.class, () -> SpaceMeetingServiceImpl.parseStamp("昨天"));
    }

    @Test
    void missingMeetingIs404() {
        when(meetingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.detail(9L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }
}
