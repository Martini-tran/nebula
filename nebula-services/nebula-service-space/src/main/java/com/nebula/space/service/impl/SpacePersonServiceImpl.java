package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.PersonContact;
import com.nebula.space.dto.me.PersonFact;
import com.nebula.space.dto.me.PersonPromise;
import com.nebula.space.dto.me.PersonSaveRequest;
import com.nebula.space.entity.SpacePerson;
import com.nebula.space.mapper.SpacePersonMapper;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.service.SpacePersonService;
import com.nebula.space.vo.me.PersonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * 人物卡服务实现
 */
@Service
@RequiredArgsConstructor
public class SpacePersonServiceImpl implements SpacePersonService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<PersonFact>> FACT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<PersonContact>> CONTACT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<PersonPromise>> PROMISE_LIST = new TypeReference<>() {
    };
    private static final String DEFAULT_GROUP = "同事";

    private final SpacePersonMapper personMapper;

    @Override
    public List<PersonVO> list() {
        Long userId = requireUserId();
        return personMapper.selectList(
                        new LambdaQueryWrapper<SpacePerson>()
                                .eq(SpacePerson::getUserId, userId)
                                .orderByAsc(SpacePerson::getCreateTime)
                                .orderByAsc(SpacePerson::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<PersonVO> search(SearchCriteria q, int limit) {
        LambdaQueryWrapper<SpacePerson> wrapper = new LambdaQueryWrapper<SpacePerson>()
                .eq(SpacePerson::getUserId, requireUserId());
        for (String who : q.getPeople()) {
            SearchCriteria.likeAny(wrapper, who, SpacePerson::getName, SpacePerson::getAlias, SpacePerson::getExtraNames);
        }
        SearchCriteria.matchTerms(wrapper, q.getTags(), SpacePerson::getPersonGroup);
        SearchCriteria.matchTerms(wrapper, q.getTerms(),
                SpacePerson::getName, SpacePerson::getAlias, SpacePerson::getExtraNames, SpacePerson::getPersonGroup,
                SpacePerson::getIntro, SpacePerson::getMemo, SpacePerson::getFacts);
        wrapper.orderByDesc(SpacePerson::getUpdateTime).orderByDesc(SpacePerson::getId).last("limit " + limit);
        return personMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public PersonVO create(PersonSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "姓名不能为空");
        }
        SpacePerson p = new SpacePerson();
        p.setUserId(userId);
        p.setAlias("");
        p.setExtraNames("[]");
        p.setPersonGroup(DEFAULT_GROUP);
        p.setColor("#0d9488");
        p.setIntro("");
        p.setFacts("[]");
        p.setMemo("");
        p.setContacts("[]");
        p.setPromises("[]");
        apply(p, req);
        try {
            personMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw duplicate(p.getName());
        }
        return toVO(p);
    }

    @Override
    public PersonVO update(Long id, PersonSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpacePerson p = requirePerson(requireUserId(), id);
        if (req.has("name") && !StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "姓名不能为空");
        }
        apply(p, req);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        p.setUpdateTime(LocalDateTime.now());
        try {
            personMapper.updateById(p);
        } catch (DuplicateKeyException e) {
            throw duplicate(p.getName());
        }
        return toVO(p);
    }

    @Override
    public void delete(Long id) {
        SpacePerson p = requirePerson(requireUserId(), id);
        personMapper.deleteById(p.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 把请求里出现的字段写到实体上
     */
    private void apply(SpacePerson p, PersonSaveRequest req) {
        if (req.has("name")) {
            p.setName(req.getName().trim());
        }
        if (req.has("alias")) {
            p.setAlias(trim(req.getAlias()));
        }
        if (req.has("extraNames")) {
            List<String> names = req.getExtraNames() == null ? List.of() : req.getExtraNames().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    // 一个字的叫法（「王」）会在正文里到处误配
                    .filter(s -> s.length() >= 2)
                    .distinct()
                    .toList();
            p.setExtraNames(writeJson(names));
        }
        if (req.has("group")) {
            p.setPersonGroup(StringUtils.hasText(req.getGroup()) ? req.getGroup().trim() : DEFAULT_GROUP);
        }
        if (req.has("color") && req.getColor() != null) {
            p.setColor(req.getColor().toLowerCase());
        }
        if (req.has("birthday")) {
            p.setBirthday(requireBirthday(req.getBirthday()));
        }
        if (req.has("contactEvery")) {
            p.setContactEvery(req.getContactEvery());
        }
        if (req.has("intro")) {
            p.setIntro(trim(req.getIntro()));
        }
        if (req.has("facts")) {
            List<PersonFact> facts = req.getFacts() == null ? List.of() : req.getFacts().stream()
                    .filter(f -> f != null && StringUtils.hasText(f.getLabel()) && StringUtils.hasText(f.getValue()))
                    .map(f -> {
                        PersonFact out = new PersonFact();
                        out.setLabel(f.getLabel().trim());
                        out.setValue(f.getValue().trim());
                        return out;
                    })
                    .toList();
            p.setFacts(writeJson(facts));
        }
        if (req.has("memo")) {
            p.setMemo(trim(req.getMemo()));
        }
        if (req.has("contacts")) {
            p.setContacts(writeJson(req.getContacts() == null ? List.of() : req.getContacts()));
        }
        if (req.has("promises")) {
            p.setPromises(writeJson(req.getPromises() == null ? List.of() : req.getPromises()));
        }
    }

    /**
     * MM-DD，且是真实存在的日子（2 月 29 日算）；空为清空
     */
    static String requireBirthday(String birthday) {
        if (!StringUtils.hasText(birthday)) {
            return null;
        }
        try {
            return MonthDay.parse("--" + birthday.trim()).toString().substring(2);
        } catch (DateTimeParseException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "生日不正确：" + birthday);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static BizException duplicate(String name) {
        return new BizException(HttpStatus.BAD_REQUEST, "已经有「" + name + "」了");
    }

    private SpacePerson requirePerson(Long userId, Long id) {
        SpacePerson p = id == null ? null : personMapper.selectOne(
                new LambdaQueryWrapper<SpacePerson>()
                        .eq(SpacePerson::getId, id)
                        .eq(SpacePerson::getUserId, userId)
        );
        if (p == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "人物不存在或已删除");
        }
        return p;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "数据格式不正确");
        }
    }

    private static <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private PersonVO toVO(SpacePerson p) {
        PersonVO vo = new PersonVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setAlias(p.getAlias());
        vo.setExtraNames(readList(p.getExtraNames(), STRING_LIST));
        vo.setGroup(p.getPersonGroup());
        vo.setColor(p.getColor());
        vo.setBirthday(p.getBirthday());
        vo.setContactEvery(p.getContactEvery());
        vo.setIntro(p.getIntro());
        vo.setFacts(readList(p.getFacts(), FACT_LIST));
        vo.setMemo(p.getMemo() == null ? "" : p.getMemo());
        vo.setContacts(readList(p.getContacts(), CONTACT_LIST));
        vo.setPromises(readList(p.getPromises(), PROMISE_LIST));
        vo.setCreateTime(p.getCreateTime() == null ? LocalDateTime.now() : p.getCreateTime());
        return vo;
    }
}
