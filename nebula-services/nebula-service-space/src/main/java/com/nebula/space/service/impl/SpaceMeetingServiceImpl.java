package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.MeetingAgendaItem;
import com.nebula.space.dto.me.MeetingAttendee;
import com.nebula.space.dto.me.MeetingQuery;
import com.nebula.space.dto.me.MeetingSaveRequest;
import com.nebula.space.entity.SpaceMeeting;
import com.nebula.space.mapper.SpaceMeetingMapper;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.util.Stamps;
import com.nebula.space.vo.me.MeetingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议记录服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceMeetingServiceImpl implements SpaceMeetingService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<MeetingAttendee>> ATTENDEE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<MeetingAgendaItem>> AGENDA_LIST = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> SYNCED_MAP = new TypeReference<>() {
    };

    private final SpaceMeetingMapper meetingMapper;

    @Override
    public List<MeetingVO> list(MeetingQuery query) {
        Long userId = requireUserId();
        MeetingQuery q = query == null ? new MeetingQuery() : query;
        return meetingMapper.selectList(
                        new LambdaQueryWrapper<SpaceMeeting>()
                                .eq(SpaceMeeting::getUserId, userId)
                                .ge(q.getFrom() != null, SpaceMeeting::getMeetingDate, q.getFrom())
                                .le(q.getTo() != null, SpaceMeeting::getMeetingDate, q.getTo())
                                .orderByAsc(SpaceMeeting::getMeetingDate)
                                .orderByAsc(SpaceMeeting::getStartTime)
                                .orderByAsc(SpaceMeeting::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public MeetingVO detail(Long id) {
        return toVO(requireMeeting(id, requireUserId()));
    }

    @Override
    public MeetingVO create(MeetingSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "会议标题不能为空");
        }
        if (req.getDate() == null || !StringUtils.hasText(req.getStartTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "会议日期和开始时间不能为空");
        }
        SpaceMeeting meeting = new SpaceMeeting();
        meeting.setUserId(userId);
        meeting.setDurationMin(30);
        MeetingAttendee me = new MeetingAttendee();
        me.setName("我");
        me.setMe(true);
        meeting.setAttendees(writeJson(List.of(me)));
        meeting.setAgenda("[]");
        meeting.setContent("");
        meeting.setStatus("planned");
        meeting.setSyncedTasks("{}");
        apply(meeting, req);
        meetingMapper.insert(meeting);
        return toVO(meeting);
    }

    @Override
    public MeetingVO update(Long id, MeetingSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceMeeting meeting = requireMeeting(id, requireUserId());
        if (req.has("title") && !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "会议标题不能为空");
        }
        if ((req.has("date") && req.getDate() == null) || (req.has("startTime") && !StringUtils.hasText(req.getStartTime()))) {
            throw new BizException(HttpStatus.BAD_REQUEST, "会议日期和开始时间不能为空");
        }
        apply(meeting, req);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        meeting.setUpdateTime(LocalDateTime.now());
        meetingMapper.updateById(meeting);
        return toVO(meeting);
    }

    @Override
    public void delete(Long id) {
        SpaceMeeting meeting = requireMeeting(id, requireUserId());
        meetingMapper.deleteById(meeting.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 把请求里出现的字段写到实体上；可空字段出现且为 null 时清空
     */
    private void apply(SpaceMeeting meeting, MeetingSaveRequest req) {
        if (req.has("title")) {
            meeting.setTitle(req.getTitle().trim());
        }
        if (req.has("date")) {
            meeting.setMeetingDate(req.getDate());
        }
        if (req.has("startTime")) {
            meeting.setStartTime(req.getStartTime());
        }
        if (req.has("durationMin") && req.getDurationMin() != null) {
            meeting.setDurationMin(req.getDurationMin());
        }
        if (req.has("template")) {
            meeting.setTemplate(StringUtils.hasText(req.getTemplate()) ? req.getTemplate() : null);
        }
        if (req.has("attendees")) {
            meeting.setAttendees(writeJson(req.getAttendees() == null ? List.of() : req.getAttendees()));
        }
        if (req.has("agenda")) {
            meeting.setAgenda(writeJson(req.getAgenda() == null ? List.of() : req.getAgenda()));
        }
        if (req.has("currentAgendaId")) {
            meeting.setCurrentAgendaId(StringUtils.hasText(req.getCurrentAgendaId()) ? req.getCurrentAgendaId() : null);
        }
        if (req.has("content")) {
            meeting.setContent(req.getContent() == null ? "" : req.getContent());
        }
        if (req.has("status") && req.getStatus() != null) {
            meeting.setStatus(req.getStatus());
        }
        if (req.has("startedAt")) {
            meeting.setStartedAt(parseStamp(req.getStartedAt()));
        }
        if (req.has("endedAt")) {
            meeting.setEndedAt(parseStamp(req.getEndedAt()));
        }
        if (req.has("syncedTasks")) {
            meeting.setSyncedTasks(writeJson(req.getSyncedTasks() == null ? Map.of() : req.getSyncedTasks()));
        }
    }

    /**
     * 空串视为清空
     */
    static LocalDateTime parseStamp(String value) {
        return Stamps.parse(value);
    }

    private SpaceMeeting requireMeeting(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "会议ID不能为空");
        }
        SpaceMeeting meeting = meetingMapper.selectOne(
                new LambdaQueryWrapper<SpaceMeeting>()
                        .eq(SpaceMeeting::getId, id)
                        .eq(SpaceMeeting::getUserId, userId)
        );
        if (meeting == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "会议不存在或已删除");
        }
        return meeting;
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

    private static <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private MeetingVO toVO(SpaceMeeting meeting) {
        MeetingVO vo = new MeetingVO();
        vo.setId(meeting.getId());
        vo.setTitle(meeting.getTitle());
        vo.setDate(meeting.getMeetingDate());
        vo.setStartTime(meeting.getStartTime());
        vo.setDurationMin(meeting.getDurationMin());
        vo.setTemplate(meeting.getTemplate());
        vo.setAttendees(readJson(meeting.getAttendees(), ATTENDEE_LIST, List.of()));
        vo.setAgenda(readJson(meeting.getAgenda(), AGENDA_LIST, List.of()));
        vo.setCurrentAgendaId(meeting.getCurrentAgendaId());
        vo.setContent(meeting.getContent() == null ? "" : meeting.getContent());
        vo.setStatus(meeting.getStatus());
        vo.setStartedAt(meeting.getStartedAt());
        vo.setEndedAt(meeting.getEndedAt());
        vo.setSyncedTasks(readJson(meeting.getSyncedTasks(), SYNCED_MAP, new LinkedHashMap<>()));
        vo.setCreateTime(meeting.getCreateTime());
        return vo;
    }
}
