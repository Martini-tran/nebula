package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.GoalKeyResult;
import com.nebula.space.dto.me.GoalSaveRequest;
import com.nebula.space.entity.SpaceGoal;
import com.nebula.space.mapper.SpaceGoalMapper;
import com.nebula.space.service.SpaceGoalService;
import com.nebula.space.vo.me.GoalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 年度目标服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceGoalServiceImpl implements SpaceGoalService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<GoalKeyResult>> KR_LIST = new TypeReference<>() {
    };
    /**
     * 这两种来源要指明是哪个习惯 / 哪个清单
     */
    private static final Set<String> SOURCES_WITH_ID = Set.of("habit", "task_list");

    private final SpaceGoalMapper goalMapper;

    @Override
    public List<GoalVO> list(Integer year) {
        Long userId = requireUserId();
        int y = year == null ? LocalDate.now().getYear() : year;
        return goalMapper.selectList(
                        new LambdaQueryWrapper<SpaceGoal>()
                                .eq(SpaceGoal::getUserId, userId)
                                .eq(SpaceGoal::getGoalYear, y)
                                .orderByAsc(SpaceGoal::getSortOrder)
                                .orderByAsc(SpaceGoal::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public GoalVO create(GoalSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目标不能为空");
        }
        if (req.getYear() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "年份不能为空");
        }
        SpaceGoal goal = new SpaceGoal();
        goal.setUserId(userId);
        goal.setIcon("lucide:target");
        goal.setKind("metric");
        goal.setTarget(BigDecimal.ZERO);
        goal.setUnit("");
        goal.setSource("manual");
        goal.setFactor(BigDecimal.ONE);
        goal.setBaseline(BigDecimal.ZERO);
        goal.setManualValue(BigDecimal.ZERO);
        goal.setKrs("[]");
        apply(goal, req);
        // 新目标排在这一年的最后
        long count = goalMapper.selectCount(
                new LambdaQueryWrapper<SpaceGoal>()
                        .eq(SpaceGoal::getUserId, userId)
                        .eq(SpaceGoal::getGoalYear, goal.getGoalYear())
        );
        goal.setSortOrder((int) count + 1);
        goalMapper.insert(goal);
        return toVO(goal);
    }

    @Override
    public GoalVO update(Long id, GoalSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceGoal goal = requireGoal(id, requireUserId());
        if (req.has("title") && !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目标不能为空");
        }
        if (req.has("year") && req.getYear() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "年份不能为空");
        }
        apply(goal, req);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        goal.setUpdateTime(LocalDateTime.now());
        goalMapper.updateById(goal);
        return toVO(goal);
    }

    @Override
    public void delete(Long id) {
        SpaceGoal goal = requireGoal(id, requireUserId());
        goalMapper.deleteById(goal.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 把请求里出现的字段写到实体上，再检查组合是否成立
     */
    private void apply(SpaceGoal goal, GoalSaveRequest req) {
        if (req.has("year")) {
            goal.setGoalYear(req.getYear());
        }
        if (req.has("title")) {
            goal.setTitle(req.getTitle().trim());
        }
        if (req.has("icon") && StringUtils.hasText(req.getIcon())) {
            goal.setIcon(req.getIcon());
        }
        if (req.has("kind") && req.getKind() != null) {
            goal.setKind(req.getKind());
        }
        if (req.has("target")) {
            goal.setTarget(orZero(req.getTarget()));
        }
        if (req.has("unit")) {
            goal.setUnit(req.getUnit() == null ? "" : req.getUnit().trim());
        }
        if (req.has("source") && req.getSource() != null) {
            goal.setSource(req.getSource());
        }
        if (req.has("sourceId")) {
            goal.setSourceId(req.getSourceId());
        }
        if (req.has("factor") && req.getFactor() != null) {
            goal.setFactor(req.getFactor());
        }
        if (req.has("baseline")) {
            goal.setBaseline(orZero(req.getBaseline()));
        }
        if (req.has("manualValue")) {
            goal.setManualValue(orZero(req.getManualValue()));
        }
        if (req.has("krs")) {
            goal.setKrs(writeJson(req.getKrs() == null ? List.of() : req.getKrs()));
        }
        // 来源不需要 ID 时清掉，免得换来源后留着指向旧习惯的 ID
        if (!SOURCES_WITH_ID.contains(goal.getSource())) {
            goal.setSourceId(null);
        } else if ("metric".equals(goal.getKind()) && goal.getSourceId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "habit".equals(goal.getSource()) ? "请选择习惯" : "请选择任务清单");
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private SpaceGoal requireGoal(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目标ID不能为空");
        }
        SpaceGoal goal = goalMapper.selectOne(
                new LambdaQueryWrapper<SpaceGoal>()
                        .eq(SpaceGoal::getId, id)
                        .eq(SpaceGoal::getUserId, userId)
        );
        if (goal == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "目标不存在或已删除");
        }
        return goal;
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

    private static List<GoalKeyResult> readKrs(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, KR_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private GoalVO toVO(SpaceGoal goal) {
        GoalVO vo = new GoalVO();
        vo.setId(goal.getId());
        vo.setYear(goal.getGoalYear());
        vo.setTitle(goal.getTitle());
        vo.setIcon(goal.getIcon());
        vo.setKind(goal.getKind());
        vo.setTarget(goal.getTarget());
        vo.setUnit(goal.getUnit());
        vo.setSource(goal.getSource());
        vo.setSourceId(goal.getSourceId());
        vo.setFactor(goal.getFactor());
        vo.setBaseline(goal.getBaseline());
        vo.setManualValue(goal.getManualValue());
        vo.setKrs(readKrs(goal.getKrs()));
        vo.setSortOrder(goal.getSortOrder());
        vo.setCreateTime(goal.getCreateTime());
        return vo;
    }
}
