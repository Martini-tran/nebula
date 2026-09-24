package com.nebula.scribe.service.impl;

import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.scribe.dto.WorkCreateRequest;
import com.nebula.scribe.dto.WorkUpdateRequest;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.vo.WorkDetailVO;
import com.nebula.scribe.vo.WorkListVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScribeWorkServiceImplTest {

    private ScribeWorkMapper mapper;
    private ScribeWorkServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper.set 需要实体的列缓存，纯 Mock 单测没有 Spring 容器来初始化它
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ScribeWork.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(ScribeWorkMapper.class);
        service = new ScribeWorkServiceImpl(mapper);
        doAnswer(inv -> {
            inv.<ScribeWork>getArgument(0).setId(100L);
            return 1;
        }).when(mapper).insert(any(ScribeWork.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createBindsCurrentUserAndDefaults() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        WorkCreateRequest req = new WorkCreateRequest();
        req.setTitle("  断刀记  ");
        req.setGenre("古代悬疑");
        req.setSummary(" ");
        req.setAudience("male");
        req.setTags(List.of("悬疑", " 悬疑 ", "", "慢热"));

        WorkListVO vo = service.create(req);

        ArgumentCaptor<ScribeWork> captor = ArgumentCaptor.forClass(ScribeWork.class);
        verify(mapper).insert(captor.capture());
        ScribeWork saved = captor.getValue();
        assertEquals(42L, saved.getUserId());
        assertEquals("断刀记", saved.getTitle());
        assertNull(saved.getSummary(), "空白简介应存 null");
        assertEquals("draft", saved.getStatus());
        assertEquals(0, saved.getWordCount());
        assertEquals(0, saved.getChapterCount());
        assertEquals("[\"悬疑\",\"慢热\"]", saved.getTags(), "标签应去空白、去重");
        assertNull(saved.getProtagonists());

        assertEquals(100L, vo.getId());
        assertEquals(List.of("悬疑", "慢热"), vo.getTags());
        assertEquals(Collections.emptyList(), vo.getProtagonists());
    }

    @Test
    void createRejectsUnknownAudience() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        WorkCreateRequest req = new WorkCreateRequest();
        req.setTitle("断刀记");
        req.setAudience("everyone");

        assertThrows(BizException.class, () -> service.create(req));
        verify(mapper, never()).insert(any(ScribeWork.class));
    }

    @Test
    void createRequiresLogin() {
        WorkCreateRequest req = new WorkCreateRequest();
        req.setTitle("断刀记");

        assertThrows(BizException.class, () -> service.create(req));
        verify(mapper, never()).insert(any(ScribeWork.class));
    }

    @Test
    void detailOfOthersWorkIsNotFound() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        // selectOne 按 id+user_id 过滤，别人的作品查不到
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.detail(7L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void detailReturnsIntroAndLists() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(owned());

        WorkDetailVO vo = service.detail(7L);

        assertEquals("旧简介", vo.getIntro());
        assertEquals(List.of("沈砚"), vo.getProtagonists());
    }

    @Test
    void updateRejectsUnknownStatus() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        WorkUpdateRequest req = new WorkUpdateRequest();
        req.setTitle("断刀记");
        req.setStatus("published");

        assertThrows(BizException.class, () -> service.update(7L, req));
        verify(mapper, never()).update(any(ScribeWork.class), any(Wrapper.class));
    }

    @Test
    void updateOfOthersWorkDoesNotWrite() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        WorkUpdateRequest req = new WorkUpdateRequest();
        req.setTitle("断刀记");

        assertThrows(BizException.class, () -> service.update(7L, req));
        verify(mapper, never()).update(any(ScribeWork.class), any(Wrapper.class));
    }

    @Test
    void updateWritesAndReturnsFreshDetail() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(owned());
        ScribeWork fresh = owned();
        fresh.setTitle("断刀记（修订）");
        fresh.setStatus("serializing");
        when(mapper.selectById(7L)).thenReturn(fresh);
        WorkUpdateRequest req = new WorkUpdateRequest();
        req.setTitle(" 断刀记（修订） ");
        req.setStatus("serializing");

        WorkDetailVO vo = service.update(7L, req);

        verify(mapper).update(any(ScribeWork.class), any(Wrapper.class));
        assertEquals("断刀记（修订）", vo.getTitle());
        assertEquals("serializing", vo.getStatus());
    }

    @Test
    void deleteStampsDeleteTimeThenSoftDeletes() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(owned());

        service.delete(7L);

        ArgumentCaptor<ScribeWork> captor = ArgumentCaptor.forClass(ScribeWork.class);
        InOrder order = inOrder(mapper);
        order.verify(mapper).updateById(captor.capture());
        order.verify(mapper).deleteById(7L);
        assertEquals(7L, captor.getValue().getId());
        assertNotNull(captor.getValue().getDeleteTime());
    }

    @Test
    void deleteOfOthersWorkIsNotFound() {
        UserContext.set(42L, "author", Collections.emptyList(), Collections.emptyList());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(7L));
        verify(mapper, never()).deleteById(any(Long.class));
    }

    private static ScribeWork owned() {
        ScribeWork work = new ScribeWork();
        work.setId(7L);
        work.setUserId(42L);
        work.setTitle("断刀记");
        work.setIntro("旧简介");
        work.setProtagonists("[\"沈砚\"]");
        work.setStatus("draft");
        work.setWordCount(0);
        work.setChapterCount(0);
        return work;
    }
}
