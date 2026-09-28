package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.PersonFact;
import com.nebula.space.dto.me.PersonPromise;
import com.nebula.space.dto.me.PersonSaveRequest;
import com.nebula.space.entity.SpacePerson;
import com.nebula.space.mapper.SpacePersonMapper;
import com.nebula.space.vo.me.PersonVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpacePersonServiceImplTest {

    private SpacePersonMapper personMapper;
    private SpacePersonServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpacePerson.class);
    }

    @BeforeEach
    void setUp() {
        personMapper = mock(SpacePersonMapper.class);
        service = new SpacePersonServiceImpl(personMapper);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static PersonFact fact(String label, String value) {
        PersonFact f = new PersonFact();
        f.setLabel(label);
        f.setValue(value);
        return f;
    }

    private static SpacePerson stored() {
        SpacePerson p = new SpacePerson();
        p.setId(7L);
        p.setUserId(42L);
        p.setName("张立");
        p.setAlias("张工");
        p.setExtraNames("[\"老张\"]");
        p.setPersonGroup("同事");
        p.setColor("#0d9488");
        p.setBirthday("11-03");
        p.setContactEvery(14);
        p.setIntro("");
        p.setFacts("[]");
        p.setMemo("旧备忘");
        p.setContacts("[]");
        p.setPromises("[]");
        return p;
    }

    @Test
    void createFillsDefaultsAndCleansLists() {
        PersonSaveRequest req = new PersonSaveRequest();
        req.setName(" 王蕾 ");
        req.setExtraNames(Arrays.asList(" 王姐 ", "王", "王姐", null));
        req.setGroup("  ");
        req.setFacts(List.of(fact(" 团队 ", " 数据平台 "), fact("", "没有名称的丢掉")));
        PersonVO vo = service.create(req);
        verify(personMapper).insert(any(SpacePerson.class));
        assertEquals("王蕾", vo.getName());
        assertEquals(List.of("王姐"), vo.getExtraNames());
        assertEquals("同事", vo.getGroup());
        assertEquals("#0d9488", vo.getColor());
        assertEquals(1, vo.getFacts().size());
        assertEquals("数据平台", vo.getFacts().get(0).getValue());
        assertTrue(vo.getPromises().isEmpty());
        assertNull(vo.getBirthday());
    }

    @Test
    void nameIsRequired() {
        BizException e = assertThrows(BizException.class, () -> service.create(new PersonSaveRequest()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void duplicateNameIs400() {
        when(personMapper.insert(any(SpacePerson.class))).thenThrow(new DuplicateKeyException("uk_space_person_name"));
        PersonSaveRequest req = new PersonSaveRequest();
        req.setName("张立");
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        assertTrue(e.getMessage().contains("张立"));
    }

    @Test
    void updateOnlyTouchesGivenFields() {
        SpacePerson p = stored();
        when(personMapper.selectOne(any(Wrapper.class))).thenReturn(p);
        PersonSaveRequest req = new PersonSaveRequest();
        req.setMemo(" 新备忘 ");
        PersonVO vo = service.update(7L, req);
        assertEquals("新备忘", vo.getMemo());
        assertEquals("11-03", vo.getBirthday());
        assertEquals(14, vo.getContactEvery());
        assertEquals(List.of("老张"), vo.getExtraNames());
        verify(personMapper).updateById(p);
    }

    @Test
    void nullClearsBirthdayAndReminder() {
        when(personMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        PersonSaveRequest req = new PersonSaveRequest();
        req.setBirthday(null);
        req.setContactEvery(null);
        PersonVO vo = service.update(7L, req);
        assertNull(vo.getBirthday());
        assertNull(vo.getContactEvery());
    }

    @Test
    void birthdayMustBeARealDay() {
        assertEquals("02-29", SpacePersonServiceImpl.requireBirthday("02-29"));
        for (String bad : new String[]{"02-30", "13-01", "00-10"}) {
            BizException e = assertThrows(BizException.class, () -> SpacePersonServiceImpl.requireBirthday(bad), bad);
            assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        }
    }

    @Test
    void promisesAreReplacedWhole() {
        when(personMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        PersonPromise pr = new PersonPromise();
        pr.setId("pr1");
        pr.setWho("them");
        pr.setText("确认扩容窗口");
        pr.setDue("2026-10-01");
        pr.setCreateTime("2026-09-28 10:00:00");
        PersonSaveRequest req = new PersonSaveRequest();
        req.setPromises(List.of(pr));
        PersonVO vo = service.update(7L, req);
        assertEquals(1, vo.getPromises().size());
        assertEquals("确认扩容窗口", vo.getPromises().get(0).getText());
        assertEquals("them", vo.getPromises().get(0).getWho());
    }

    @Test
    void blankNameOnUpdateIs400() {
        when(personMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        PersonSaveRequest req = new PersonSaveRequest();
        req.setName(" ");
        BizException e = assertThrows(BizException.class, () -> service.update(7L, req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(personMapper, never()).updateById(any(SpacePerson.class));
    }

    @Test
    void someoneElsesCardIs404() {
        BizException e = assertThrows(BizException.class, () -> service.delete(7L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
        verify(personMapper, never()).deleteById(any(Long.class));
    }
}
