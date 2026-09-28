package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.entity.SpaceUserSetting;
import com.nebula.space.mapper.SpaceUserSettingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceSettingServiceImplTest {

    private SpaceUserSettingMapper settingMapper;
    private SpaceSettingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceUserSetting.class);
    }

    @BeforeEach
    void setUp() {
        settingMapper = mock(SpaceUserSettingMapper.class);
        service = new SpaceSettingServiceImpl(settingMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private SpaceUserSetting given(String json) {
        SpaceUserSetting row = new SpaceUserSetting();
        row.setId(3L);
        row.setUserId(42L);
        row.setSettings(json);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(row);
        return row;
    }

    @Test
    void neverSavedIsEmptyObject() {
        assertTrue(service.get().isEmpty());
    }

    @Test
    void getReturnsNestedValues() {
        given("{\"weekStart\":0,\"eveningReview\":{\"enabled\":false,\"time\":\"21:00\"},\"disabledModules\":[\"ledger\"]}");
        Map<String, Object> s = service.get();
        assertEquals(0, s.get("weekStart"));
        assertEquals(Map.of("enabled", false, "time", "21:00"), s.get("eveningReview"));
        assertEquals(List.of("ledger"), s.get("disabledModules"));
    }

    @Test
    void corruptedJsonFallsBackToEmpty() {
        given("{not json");
        assertTrue(service.get().isEmpty());
    }

    @Test
    void firstSaveInsertsForCurrentUser() {
        service.save(Map.of("noteTtlDays", 3));
        ArgumentCaptor<SpaceUserSetting> saved = ArgumentCaptor.forClass(SpaceUserSetting.class);
        verify(settingMapper).insert(saved.capture());
        assertEquals(42L, saved.getValue().getUserId());
        assertEquals("{\"noteTtlDays\":3}", saved.getValue().getSettings());
    }

    @Test
    void saveOverwritesWholeObject() {
        SpaceUserSetting row = given("{\"noteTtlDays\":3,\"home\":\"notes\"}");
        service.save(Map.of("home", "today"));
        verify(settingMapper, never()).insert(any(SpaceUserSetting.class));
        verify(settingMapper).updateById(row);
        assertEquals("{\"home\":\"today\"}", row.getSettings());
    }

    @Test
    void oversizedIs400() {
        BizException e = assertThrows(BizException.class, () -> service.save(Map.of("x", "a".repeat(SpaceSettingServiceImpl.MAX_LENGTH))));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }
}
