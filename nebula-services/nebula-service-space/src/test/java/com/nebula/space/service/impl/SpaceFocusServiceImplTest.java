package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.FocusSessionCreateRequest;
import com.nebula.space.entity.SpaceFocusSession;
import com.nebula.space.mapper.SpaceFocusSessionMapper;
import com.nebula.space.vo.me.FocusSessionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpaceFocusServiceImplTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpaceFocusSessionMapper sessionMapper;
    private SpaceFocusServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceFocusSession.class);
    }

    @BeforeEach
    void setUp() {
        sessionMapper = mock(SpaceFocusSessionMapper.class);
        service = new SpaceFocusServiceImpl(sessionMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static FocusSessionCreateRequest body(String json) throws Exception {
        return JSON.readValue(json, FocusSessionCreateRequest.class);
    }

    @Test
    void createParsesFrontendStampAndBindsCurrentUser() throws Exception {
        FocusSessionVO vo = service.create(body("{\"taskId\":\"12\",\"taskTitle\":\" 写周报 \",\"startedAt\":\"2026-09-27 09:30:00\","
                + "\"endedAt\":\"2026-09-27 09:55:00\",\"plannedMin\":25,\"actualMin\":25,\"status\":\"done\",\"interruptions\":1}"));
        ArgumentCaptor<SpaceFocusSession> saved = ArgumentCaptor.forClass(SpaceFocusSession.class);
        verify(sessionMapper).insert(saved.capture());
        assertEquals(42L, saved.getValue().getUserId());
        assertEquals(12L, vo.getTaskId());
        assertEquals("写周报", vo.getTaskTitle());
        assertEquals(LocalDateTime.of(2026, 9, 27, 9, 30), vo.getStartedAt());
        assertEquals(LocalDateTime.of(2026, 9, 27, 9, 55), vo.getEndedAt());
        assertEquals(1, vo.getInterruptions());
    }

    @Test
    void createWithoutTaskDefaultsInterruptions() throws Exception {
        FocusSessionVO vo = service.create(body("{\"taskId\":null,\"taskTitle\":\"自由专注\",\"startedAt\":\"2026-09-27T20:00:00\","
                + "\"endedAt\":\"2026-09-27T20:03:00\",\"plannedMin\":25,\"actualMin\":3,\"status\":\"abandoned\"}"));
        assertNull(vo.getTaskId());
        assertEquals(0, vo.getInterruptions());
        assertEquals("abandoned", vo.getStatus());
    }

    @Test
    void endBeforeStartIs400() {
        BizException e = assertThrows(BizException.class, () -> service.create(body("{\"taskTitle\":\"x\",\"startedAt\":\"2026-09-27 10:00:00\","
                + "\"endedAt\":\"2026-09-27 09:00:00\",\"plannedMin\":25,\"actualMin\":1,\"status\":\"done\"}")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void garbageStampIs400() {
        BizException e = assertThrows(BizException.class, () -> service.create(body("{\"taskTitle\":\"x\",\"startedAt\":\"刚才\","
                + "\"endedAt\":\"2026-09-27 09:00:00\",\"plannedMin\":25,\"actualMin\":1,\"status\":\"done\"}")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }
}
