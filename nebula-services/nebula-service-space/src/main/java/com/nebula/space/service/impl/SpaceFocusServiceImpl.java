package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.FocusSessionCreateRequest;
import com.nebula.space.dto.me.FocusSessionQuery;
import com.nebula.space.entity.SpaceFocusSession;
import com.nebula.space.mapper.SpaceFocusSessionMapper;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.util.Stamps;
import com.nebula.space.vo.me.FocusSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 专注记录服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceFocusServiceImpl implements SpaceFocusService {

    private final SpaceFocusSessionMapper sessionMapper;

    @Override
    public List<FocusSessionVO> list(FocusSessionQuery query) {
        Long userId = requireUserId();
        FocusSessionQuery q = query == null ? new FocusSessionQuery() : query;
        return sessionMapper.selectList(
                        new LambdaQueryWrapper<SpaceFocusSession>()
                                .eq(SpaceFocusSession::getUserId, userId)
                                .eq(q.getTaskId() != null, SpaceFocusSession::getTaskId, q.getTaskId())
                                .ge(q.getFrom() != null, SpaceFocusSession::getStartedAt,
                                        q.getFrom() == null ? null : q.getFrom().atStartOfDay())
                                .lt(q.getTo() != null, SpaceFocusSession::getStartedAt,
                                        q.getTo() == null ? null : q.getTo().plusDays(1).atStartOfDay())
                                .orderByAsc(SpaceFocusSession::getStartedAt)
                                .orderByAsc(SpaceFocusSession::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public FocusSessionVO create(FocusSessionCreateRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getTaskTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "任务标题不能为空");
        }
        LocalDateTime startedAt = Stamps.parse(req.getStartedAt());
        LocalDateTime endedAt = Stamps.parse(req.getEndedAt());
        if (startedAt == null || endedAt == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "开始和结束时间不能为空");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        SpaceFocusSession session = new SpaceFocusSession();
        session.setUserId(userId);
        // 任务可能在计时途中被删，标题存快照，这里不校验任务是否还在
        session.setTaskId(req.getTaskId());
        session.setTaskTitle(req.getTaskTitle().trim());
        session.setStartedAt(startedAt);
        session.setEndedAt(endedAt);
        session.setPlannedMin(req.getPlannedMin());
        session.setActualMin(req.getActualMin());
        session.setStatus(req.getStatus());
        session.setInterruptions(req.getInterruptions() == null ? 0 : req.getInterruptions());
        sessionMapper.insert(session);
        return toVO(session);
    }

    // ----------------------------------------------------------------- 内部工具

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private FocusSessionVO toVO(SpaceFocusSession session) {
        FocusSessionVO vo = new FocusSessionVO();
        vo.setId(session.getId());
        vo.setTaskId(session.getTaskId());
        vo.setTaskTitle(session.getTaskTitle());
        vo.setStartedAt(session.getStartedAt());
        vo.setEndedAt(session.getEndedAt());
        vo.setPlannedMin(session.getPlannedMin());
        vo.setActualMin(session.getActualMin());
        vo.setStatus(session.getStatus());
        vo.setInterruptions(session.getInterruptions());
        return vo;
    }
}
