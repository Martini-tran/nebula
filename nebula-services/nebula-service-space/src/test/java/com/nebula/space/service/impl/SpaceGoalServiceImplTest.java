package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.GoalSaveRequest;
import com.nebula.space.entity.SpaceGoal;
import com.nebula.space.mapper.SpaceGoalMapper;
import com.nebula.space.vo.me.GoalVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceGoalServiceImplTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpaceGoalMapper goalMapper;
    private SpaceGoalServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceGoal.class);
    }

    @BeforeEach
    void setUp() {
        goalMapper = mock(SpaceGoalMapper.class);
        service = new SpaceGoalServiceImpl(goalMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static GoalSaveRequest body(String json) throws Exception {
        return JSON.readValue(json, GoalSaveRequest.class);
    }

    private SpaceGoal given() {
        SpaceGoal g = new SpaceGoal();
        g.setId(7L);
        g.setUserId(42L);
        g.setGoalYear(2026);
        g.setTitle("跑满 1200 公里");
        g.setIcon("lucide:footprints");
        g.setKind("metric");
        g.setTarget(new BigDecimal("1200"));
        g.setUnit("km");
        g.setSource("habit");
        g.setSourceId(3L);
        g.setFactor(new BigDecimal("5"));
        g.setBaseline(BigDecimal.ZERO);
        g.setManualValue(BigDecimal.ZERO);
        g.setKrs("[]");
        g.setSortOrder(1);
        when(goalMapper.selectOne(any(Wrapper.class))).thenReturn(g);
        return g;
    }

    @Test
    void createAppendsToYearAndKeepsHabitSource() throws Exception {
        when(goalMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        GoalVO vo = service.create(body("{\"year\":2026,\"title\":\" 跑满 1200 公里 \",\"kind\":\"metric\",\"target\":1200,"
                + "\"unit\":\"km\",\"source\":\"habit\",\"sourceId\":\"3\",\"factor\":5}"));
        assertEquals("跑满 1200 公里", vo.getTitle());
        assertEquals(3L, vo.getSourceId());
        assertEquals(3, vo.getSortOrder());
        assertEquals(0, new BigDecimal("5").compareTo(vo.getFactor()));
        assertEquals("lucide:target", vo.getIcon());
    }

    @Test
    void habitSourceNeedsHabitId() {
        BizException e = assertThrows(BizException.class, () -> service.create(body("{\"year\":2026,\"title\":\"x\",\"source\":\"habit\"}")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void switchingToManualClearsSourceId() throws Exception {
        given();
        GoalVO vo = service.update(7L, body("{\"source\":\"manual\",\"manualValue\":4}"));
        assertNull(vo.getSourceId());
        assertEquals(0, new BigDecimal("4").compareTo(vo.getManualValue()));
    }

    @Test
    void toggleKeyResultOnlyTouchesKrs() throws Exception {
        SpaceGoal g = given();
        g.setKind("milestone");
        GoalVO vo = service.update(7L, body("{\"krs\":[{\"id\":\"k1\",\"title\":\"发布 1.0\",\"done\":true,\"doneDate\":\"2026-09-27\",\"listId\":\"12\"}]}"));
        assertEquals(1, vo.getKrs().size());
        assertTrue(vo.getKrs().get(0).isDone());
        assertEquals("12", vo.getKrs().get(0).getListId());
        assertEquals("跑满 1200 公里", vo.getTitle());
    }

    @Test
    void otherUsersGoalIs404() {
        when(goalMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.delete(9L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }
}
