package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.HabitFreq;
import com.nebula.space.dto.me.HabitLogQuery;
import com.nebula.space.dto.me.HabitLogSetRequest;
import com.nebula.space.dto.me.HabitSaveRequest;
import com.nebula.space.entity.SpaceHabit;
import com.nebula.space.entity.SpaceHabitLog;
import com.nebula.space.mapper.SpaceHabitLogMapper;
import com.nebula.space.mapper.SpaceHabitMapper;
import com.nebula.space.service.SpaceHabitService;
import com.nebula.space.vo.me.HabitLogVO;
import com.nebula.space.vo.me.HabitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 习惯与打卡服务实现
 *
 * <p>打卡是「某习惯某天的值」：同一天只有一条，写 0 等于取消打卡（物理删除）。
 * 只能打今天和补前 {@value #BACKFILL_DAYS} 天，与前端 HABIT_BACKFILL_DAYS 一致。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceHabitServiceImpl implements SpaceHabitService {

    /**
     * 补打卡最多允许补前几天
     */
    static final int BACKFILL_DAYS = 2;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final SpaceHabitMapper habitMapper;
    private final SpaceHabitLogMapper logMapper;

    @Override
    public List<HabitVO> list(boolean includeArchived) {
        Long userId = requireUserId();
        return habitMapper.selectList(
                        new LambdaQueryWrapper<SpaceHabit>()
                                .eq(SpaceHabit::getUserId, userId)
                                .eq(!includeArchived, SpaceHabit::getArchived, 0)
                                .orderByAsc(SpaceHabit::getSortOrder)
                                .orderByAsc(SpaceHabit::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public HabitVO create(HabitSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "习惯名称不能为空");
        }
        SpaceHabit habit = new SpaceHabit();
        habit.setUserId(userId);
        habit.setIcon("lucide:circle-check");
        habit.setKind("check");
        habit.setTarget(1);
        habit.setUnit("");
        habit.setFreq("{\"type\":\"daily\"}");
        habit.setReminders("[]");
        habit.setFromFocus(0);
        habit.setArchived(0);
        habit.setSortOrder(Math.toIntExact(habitMapper.selectCount(
                new LambdaQueryWrapper<SpaceHabit>().eq(SpaceHabit::getUserId, userId))));
        apply(habit, req);
        habitMapper.insert(habit);
        return toVO(habit);
    }

    @Override
    public HabitVO update(Long id, HabitSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceHabit habit = requireHabit(id, requireUserId());
        if (req.getName() != null && !StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "习惯名称不能为空");
        }
        apply(habit, req);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        habit.setUpdateTime(LocalDateTime.now());
        habitMapper.updateById(habit);
        return toVO(habit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = requireUserId();
        SpaceHabit habit = requireHabit(id, userId);
        logMapper.delete(new LambdaQueryWrapper<SpaceHabitLog>()
                .eq(SpaceHabitLog::getUserId, userId)
                .eq(SpaceHabitLog::getHabitId, habit.getId()));
        habitMapper.deleteById(habit.getId());
    }

    @Override
    public List<HabitLogVO> logs(HabitLogQuery query) {
        Long userId = requireUserId();
        HabitLogQuery q = query == null ? new HabitLogQuery() : query;
        return logMapper.selectList(
                        new LambdaQueryWrapper<SpaceHabitLog>()
                                .eq(SpaceHabitLog::getUserId, userId)
                                .eq(q.getHabitId() != null, SpaceHabitLog::getHabitId, q.getHabitId())
                                .ge(q.getFrom() != null, SpaceHabitLog::getLogDate, q.getFrom())
                                .le(q.getTo() != null, SpaceHabitLog::getLogDate, q.getTo())
                                .orderByAsc(SpaceHabitLog::getLogDate)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public HabitLogVO setLog(Long habitId, LocalDate date, HabitLogSetRequest req) {
        if (date == null || req == null || req.getValue() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceHabit habit = requireHabit(habitId, userId);
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "还没到这天");
        }
        if (date.isBefore(today.minusDays(BACKFILL_DAYS))) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只能补打前 " + BACKFILL_DAYS + " 天");
        }

        SpaceHabitLog existing = findLog(habit.getId(), date);
        if (req.getValue() <= 0) {
            if (existing != null) {
                logMapper.deleteById(existing.getId());
            }
            return null;
        }
        if (existing == null) {
            SpaceHabitLog log = new SpaceHabitLog();
            log.setUserId(userId);
            log.setHabitId(habit.getId());
            log.setLogDate(date);
            log.setValue(req.getValue());
            log.setNote(req.getNote() == null ? "" : req.getNote().trim());
            log.setBackfilled(date.isBefore(today) ? 1 : 0);
            log.setLogTime(LocalDateTime.now());
            try {
                logMapper.insert(log);
                return toVO(log);
            } catch (DuplicateKeyException e) {
                // 同一天并发写入（如专注记录与手动打卡同时到达）：另一条已插入，改为覆盖它
                existing = findLog(habit.getId(), date);
                if (existing == null) {
                    throw e;
                }
            }
        }
        existing.setValue(req.getValue());
        if (req.getNote() != null) {
            existing.setNote(req.getNote().trim());
        }
        existing.setLogTime(LocalDateTime.now());
        existing.setUpdateTime(LocalDateTime.now());
        logMapper.updateById(existing);
        return toVO(existing);
    }

    // ----------------------------------------------------------------- 内部工具

    private void apply(SpaceHabit habit, HabitSaveRequest req) {
        if (req.getName() != null) {
            habit.setName(req.getName().trim());
        }
        if (StringUtils.hasText(req.getIcon())) {
            habit.setIcon(req.getIcon().trim());
        }
        if (req.getKind() != null) {
            habit.setKind(req.getKind());
        }
        if (req.getTarget() != null) {
            habit.setTarget(req.getTarget());
        }
        if (req.getUnit() != null) {
            habit.setUnit(req.getUnit().trim());
        }
        if (req.getFreq() != null) {
            habit.setFreq(writeJson(normalizeFreq(req.getFreq())));
        }
        if (req.getReminders() != null) {
            habit.setReminders(writeJson(req.getReminders().stream().distinct().sorted().toList()));
        }
        if (req.getFromFocus() != null) {
            habit.setFromFocus(req.getFromFocus() ? 1 : 0);
        }
        if (req.getArchived() != null) {
            habit.setArchived(req.getArchived() ? 1 : 0);
        }
        if (req.getSortOrder() != null) {
            habit.setSortOrder(req.getSortOrder());
        }
    }

    /**
     * 只保留频率类型用得到的字段，并补齐必填项
     */
    private static HabitFreq normalizeFreq(HabitFreq freq) {
        HabitFreq out = new HabitFreq();
        out.setType(freq.getType());
        switch (freq.getType()) {
            case "weekly_n" -> {
                if (freq.getN() == null) {
                    throw new BizException(HttpStatus.BAD_REQUEST, "每周几次不能为空");
                }
                out.setN(freq.getN());
            }
            case "weekdays" -> {
                if (freq.getDays() == null || freq.getDays().isEmpty()) {
                    throw new BizException(HttpStatus.BAD_REQUEST, "请至少选一天");
                }
                out.setDays(freq.getDays().stream().distinct().sorted().toList());
            }
            default -> {
                // daily 没有参数
            }
        }
        return out;
    }

    private SpaceHabitLog findLog(Long habitId, LocalDate date) {
        return logMapper.selectOne(
                new LambdaQueryWrapper<SpaceHabitLog>()
                        .eq(SpaceHabitLog::getHabitId, habitId)
                        .eq(SpaceHabitLog::getLogDate, date)
        );
    }

    private SpaceHabit requireHabit(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "习惯ID不能为空");
        }
        SpaceHabit habit = habitMapper.selectOne(
                new LambdaQueryWrapper<SpaceHabit>()
                        .eq(SpaceHabit::getId, id)
                        .eq(SpaceHabit::getUserId, userId)
        );
        if (habit == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "习惯不存在或已删除");
        }
        return habit;
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

    private static HabitFreq readFreq(String json) {
        try {
            return StringUtils.hasText(json) ? JSON.readValue(json, HabitFreq.class) : dailyFreq();
        } catch (JsonProcessingException e) {
            return dailyFreq();
        }
    }

    private static HabitFreq dailyFreq() {
        HabitFreq freq = new HabitFreq();
        freq.setType("daily");
        return freq;
    }

    private static List<String> readReminders(String json) {
        try {
            return StringUtils.hasText(json) ? JSON.readValue(json, STRING_LIST) : List.of();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private HabitVO toVO(SpaceHabit habit) {
        HabitVO vo = new HabitVO();
        vo.setId(habit.getId());
        vo.setName(habit.getName());
        vo.setIcon(habit.getIcon());
        vo.setKind(habit.getKind());
        vo.setTarget(habit.getTarget());
        vo.setUnit(habit.getUnit() == null ? "" : habit.getUnit());
        vo.setFreq(readFreq(habit.getFreq()));
        vo.setReminders(readReminders(habit.getReminders()));
        vo.setFromFocus(Objects.equals(habit.getFromFocus(), 1));
        vo.setArchived(Objects.equals(habit.getArchived(), 1));
        vo.setSortOrder(habit.getSortOrder());
        vo.setCreateTime(habit.getCreateTime());
        return vo;
    }

    private HabitLogVO toVO(SpaceHabitLog log) {
        HabitLogVO vo = new HabitLogVO();
        vo.setId(log.getId());
        vo.setHabitId(log.getHabitId());
        vo.setDate(log.getLogDate());
        vo.setValue(log.getValue());
        vo.setNote(log.getNote() == null ? "" : log.getNote());
        vo.setBackfilled(Objects.equals(log.getBackfilled(), 1));
        vo.setTime(log.getLogTime());
        return vo;
    }
}
