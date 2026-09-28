package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.entity.SpaceTask;
import com.nebula.space.mapper.SpaceTaskMapper;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.vo.me.TaskCompleteVO;
import com.nebula.space.vo.me.TaskStatsVO;
import com.nebula.space.vo.me.TaskVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceTaskServiceImplTest {

    // 与运行时一致：Spring Boot 4 的 MVC 用 Jackson 3，内置 java.time 支持
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpaceTaskMapper taskMapper;
    private SpaceTaskListService listService;
    private SpaceTaskServiceImpl service;
    private final LocalDate today = LocalDate.now();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceTask.class);
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(SpaceTaskMapper.class);
        listService = mock(SpaceTaskListService.class);
        service = new SpaceTaskServiceImpl(taskMapper, listService);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static TaskSaveRequest body(String json) throws Exception {
        return JSON.readValue(json, TaskSaveRequest.class);
    }

    private SpaceTask given(String repeat, String subtasks) {
        SpaceTask t = new SpaceTask();
        t.setId(5L);
        t.setUserId(42L);
        t.setListId(3L);
        t.setTitle("周报");
        t.setDueDate(LocalDate.of(2026, 9, 25));
        t.setDueTime("09:00");
        t.setPriority(2);
        t.setDone(0);
        t.setRepeatRule(repeat);
        t.setSubtasks(subtasks);
        t.setNote("");
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(t);
        return t;
    }

    @Test
    void jsonNullIsTrackedAsPresent() throws Exception {
        TaskSaveRequest r = body("{\"dueDate\":null,\"title\":\"x\"}");
        assertTrue(r.has("dueDate"));
        assertNull(r.getDueDate());
        assertFalse(r.has("dueTime"));
    }

    @Test
    void createDefaultsAndSource() throws Exception {
        TaskVO vo = service.create(body("{\"title\":\"  买  猫粮 \",\"source\":{\"type\":\"note\",\"id\":12,\"label\":\"猫粮\"}}"));
        assertEquals("买 猫粮", vo.getTitle());
        assertEquals(0, vo.getPriority());
        assertFalse(vo.getDone());
        assertNull(vo.getDueDate());
        assertEquals(List.of(), vo.getSubtasks());
        assertEquals("note", vo.getSource().getType());
        assertEquals("12", vo.getSource().getId());
        verify(taskMapper).insert(any(SpaceTask.class));
    }

    @Test
    void createRequiresTitle() {
        BizException e = assertThrows(BizException.class, () -> service.create(body("{\"note\":\"x\"}")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void createChecksListOwnership() throws Exception {
        doThrow(new BizException(HttpStatus.NOT_FOUND, "清单不存在或已删除")).when(listService).requireOwned(eq(99L), eq(42L));
        assertThrows(BizException.class, () -> service.create(body("{\"title\":\"a\",\"listId\":99}")));
        verify(taskMapper, never()).insert(any(SpaceTask.class));
    }

    @Test
    void updateOnlyTouchesPresentFieldsAndNullClears() throws Exception {
        given(null, "[]");
        TaskVO vo = service.update(5L, body("{\"dueDate\":null,\"dueTime\":null}"));
        assertNull(vo.getDueDate());
        assertNull(vo.getDueTime());
        assertEquals(3L, vo.getListId());
        assertEquals(2, vo.getPriority());
        assertEquals("周报", vo.getTitle());
    }

    @Test
    void completeRepeatingTaskCreatesNextWithSubtasksReset() {
        given("{\"type\":\"weekly\",\"days\":[1]}", "[{\"id\":\"s1\",\"title\":\"写\",\"done\":true}]");
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        TaskCompleteVO result = service.complete(5L, true);

        assertTrue(result.getTask().getDone());
        assertNotNull(result.getTask().getDoneTime());
        ArgumentCaptor<SpaceTask> inserted = ArgumentCaptor.forClass(SpaceTask.class);
        verify(taskMapper).insert(inserted.capture());
        SpaceTask next = inserted.getValue();
        // 9/25 周五之后的周一
        assertEquals(LocalDate.of(2026, 9, 28), next.getDueDate());
        assertEquals(0, next.getDone());
        assertEquals(3L, next.getListId());
        assertFalse(result.getNext().getSubtasks().get(0).isDone());
        assertEquals("weekly", result.getNext().getRepeat().getType());
    }

    @Test
    void completeAgainDoesNotDuplicateNext() {
        given("{\"type\":\"daily\"}", "[]");
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        TaskCompleteVO result = service.complete(5L, true);
        assertNull(result.getNext());
        verify(taskMapper, never()).insert(any(SpaceTask.class));
    }

    @Test
    void undoClearsDoneTime() {
        SpaceTask t = given("{\"type\":\"daily\"}", "[]");
        t.setDone(1);
        TaskCompleteVO result = service.complete(5L, false);
        assertFalse(result.getTask().getDone());
        assertNull(result.getTask().getDoneTime());
        assertNull(result.getNext());
    }

    @Test
    void statsCountsOpenTasks() {
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                task(null, null),
                task(today.minusDays(1), 3L),
                task(today, 3L),
                task(today.plusDays(6), null),
                task(today.plusDays(7), 4L)
        ));
        TaskStatsVO s = service.stats();
        assertEquals(1, s.getInbox());
        assertEquals(2, s.getToday());
        assertEquals(1, s.getOverdue());
        assertEquals(2, s.getPlan());
        assertEquals(2, s.getLists().get("3"));
        assertEquals(1, s.getLists().get("4"));
    }

    @Test
    void missingTaskIs404() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.detail(9L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    private static SpaceTask task(LocalDate due, Long listId) {
        SpaceTask t = new SpaceTask();
        t.setDueDate(due);
        t.setListId(listId);
        return t;
    }
}
