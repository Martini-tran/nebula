package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.RepeatRule;
import com.nebula.space.dto.me.SubTask;
import com.nebula.space.dto.me.TaskQuery;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.dto.me.TaskSource;
import com.nebula.space.entity.SpaceTask;
import com.nebula.space.mapper.SpaceTaskMapper;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.util.RepeatRules;
import com.nebula.space.vo.me.TaskCompleteVO;
import com.nebula.space.vo.me.TaskListVO;
import com.nebula.space.vo.me.TaskStatsVO;
import com.nebula.space.vo.me.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 任务服务实现
 *
 * <p>视图口径（与前端一致）：</p>
 * <ul>
 *     <li>收件箱 = 未完成且没日期</li>
 *     <li>今天 = 未完成且日期 ≤ 今天（含过期）</li>
 *     <li>计划 = 未完成且日期在今天起 7 天内</li>
 * </ul>
 * <p>完成一条重复任务时按规则生成下一次（子任务重置为未完成），不在原任务上改日期；
 * 撤销后再完成不会重复生成。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceTaskServiceImpl implements SpaceTaskService {

    /**
     * 已完成视图最多返回的条数
     */
    private static final int DONE_LIMIT = 200;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<SubTask>> SUBTASK_LIST = new TypeReference<>() {
    };

    /**
     * 未完成任务的排序：有日期的在前按日期，全天排在有时间的后面，同一时刻优先级高的在前
     */
    private static final Comparator<SpaceTask> OPEN_ORDER = Comparator
            .comparing(SpaceTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SpaceTask::getDueTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SpaceTask::getPriority, Comparator.nullsLast(Comparator.reverseOrder()));

    private final SpaceTaskMapper taskMapper;
    private final SpaceTaskListService listService;

    @Override
    public List<TaskVO> list(TaskQuery query) {
        Long userId = requireUserId();
        TaskQuery q = query == null ? new TaskQuery() : query;
        String view = StringUtils.hasText(q.getView()) ? q.getView() : "all";
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<SpaceTask> wrapper = new LambdaQueryWrapper<SpaceTask>()
                .eq(SpaceTask::getUserId, userId)
                .eq(q.getListId() != null, SpaceTask::getListId, q.getListId())
                .eq(StringUtils.hasText(q.getSourceType()), SpaceTask::getSourceType, q.getSourceType())
                .eq(StringUtils.hasText(q.getSourceType()) && StringUtils.hasText(q.getSourceId()),
                        SpaceTask::getSourceId, q.getSourceId());
        switch (view) {
            case "inbox" -> wrapper.eq(SpaceTask::getDone, 0).isNull(SpaceTask::getDueDate);
            case "today" -> wrapper.eq(SpaceTask::getDone, 0).le(SpaceTask::getDueDate, today);
            case "plan" -> wrapper.eq(SpaceTask::getDone, 0).between(SpaceTask::getDueDate, today, today.plusDays(6));
            case "done" -> wrapper.eq(SpaceTask::getDone, 1)
                    .orderByDesc(SpaceTask::getDoneTime)
                    .last("limit " + DONE_LIMIT);
            default -> {
                // all：不加条件
            }
        }
        List<SpaceTask> rows = taskMapper.selectList(wrapper);
        if (!"done".equals(view)) {
            rows = rows.stream().sorted(OPEN_ORDER).toList();
        }
        return rows.stream().map(this::toVO).toList();
    }

    @Override
    public List<TaskVO> listForDays(LocalDate from, LocalDate to, LocalDate today, LocalDateTime doneSince) {
        Long userId = requireUserId();
        List<SpaceTask> rows = taskMapper.selectList(
                new LambdaQueryWrapper<SpaceTask>()
                        .eq(SpaceTask::getUserId, userId)
                        .and(w -> {
                            w.between(SpaceTask::getDueDate, from, to)
                                    .or(x -> x.eq(SpaceTask::getDone, 0).lt(SpaceTask::getDueDate, today));
                            if (doneSince != null) {
                                w.or(x -> x.eq(SpaceTask::getDone, 1).ge(SpaceTask::getDoneTime, doneSince));
                            }
                        })
        );
        return rows.stream().sorted(OPEN_ORDER).map(this::toVO).toList();
    }

    @Override
    public List<TaskVO> listOpenOrDoneSince(LocalDateTime doneSince) {
        Long userId = requireUserId();
        List<SpaceTask> rows = taskMapper.selectList(
                new LambdaQueryWrapper<SpaceTask>()
                        .eq(SpaceTask::getUserId, userId)
                        .and(w -> w.eq(SpaceTask::getDone, 0)
                                .or(x -> x.eq(SpaceTask::getDone, 1).ge(SpaceTask::getDoneTime, doneSince)))
        );
        return rows.stream().sorted(OPEN_ORDER).map(this::toVO).toList();
    }

    @Override
    public List<TaskVO> search(SearchCriteria q, int limit) {
        Long userId = requireUserId();
        LambdaQueryWrapper<SpaceTask> wrapper = new LambdaQueryWrapper<SpaceTask>().eq(SpaceTask::getUserId, userId);
        if (!q.getTags().isEmpty()) {
            // #xx 对任务是清单名
            List<Long> listIds = listService.list().stream()
                    .filter(l -> q.getTags().stream().allMatch(tag -> l.getName().toLowerCase(Locale.ROOT).contains(tag)))
                    .map(TaskListVO::getId)
                    .toList();
            if (listIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(SpaceTask::getListId, listIds);
        }
        LocalDate today = LocalDate.now();
        boolean done = q.hasState("done");
        boolean open = q.hasState("open");
        boolean overdue = q.hasState("overdue");
        if (done && !open && !overdue) {
            wrapper.eq(SpaceTask::getDone, 1);
        } else if (!done && open) {
            wrapper.eq(SpaceTask::getDone, 0);
        } else if (!done && overdue) {
            wrapper.eq(SpaceTask::getDone, 0).lt(SpaceTask::getDueDate, today);
        } else if (done && overdue && !open) {
            wrapper.and(w -> w.eq(SpaceTask::getDone, 1)
                    .or(x -> x.eq(SpaceTask::getDone, 0).lt(SpaceTask::getDueDate, today)));
        }
        if (!q.getPeopleNames().isEmpty()) {
            SearchCriteria.likeAnyWord(wrapper, SpaceTask::getSourceLabel, q.getPeopleNames());
        }
        // 日期按截止日，没有截止日的按创建日
        LocalDate after = q.afterDate();
        if (after != null) {
            wrapper.and(w -> w.ge(SpaceTask::getDueDate, after)
                    .or(x -> x.isNull(SpaceTask::getDueDate).ge(SpaceTask::getCreateTime, after.atStartOfDay())));
        }
        LocalDate before = q.beforeDate();
        if (before != null) {
            wrapper.and(w -> w.le(SpaceTask::getDueDate, before)
                    .or(x -> x.isNull(SpaceTask::getDueDate).lt(SpaceTask::getCreateTime, before.plusDays(1).atStartOfDay())));
        }
        SearchCriteria.matchTerms(wrapper, q.getTerms(), SpaceTask::getTitle, SpaceTask::getNote, SpaceTask::getSubtasks);
        wrapper.orderByDesc(SpaceTask::getCreateTime).orderByDesc(SpaceTask::getId).last("limit " + limit);
        return taskMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public TaskVO detail(Long id) {
        return toVO(requireTask(id, requireUserId()));
    }

    @Override
    public TaskStatsVO stats() {
        Long userId = requireUserId();
        LocalDate today = LocalDate.now();
        LocalDate planEnd = today.plusDays(6);
        List<SpaceTask> open = taskMapper.selectList(
                new LambdaQueryWrapper<SpaceTask>()
                        .select(SpaceTask::getId, SpaceTask::getListId, SpaceTask::getDueDate)
                        .eq(SpaceTask::getUserId, userId)
                        .eq(SpaceTask::getDone, 0)
        );
        int inbox = 0;
        int dueToday = 0;
        int overdue = 0;
        int plan = 0;
        Map<String, Integer> lists = new LinkedHashMap<>();
        for (SpaceTask task : open) {
            LocalDate due = task.getDueDate();
            if (due == null) {
                inbox++;
            } else {
                if (!due.isAfter(today)) {
                    dueToday++;
                }
                if (due.isBefore(today)) {
                    overdue++;
                }
                if (!due.isBefore(today) && !due.isAfter(planEnd)) {
                    plan++;
                }
            }
            if (task.getListId() != null) {
                lists.merge(String.valueOf(task.getListId()), 1, Integer::sum);
            }
        }
        TaskStatsVO vo = new TaskStatsVO();
        vo.setInbox(inbox);
        vo.setToday(dueToday);
        vo.setOverdue(overdue);
        vo.setPlan(plan);
        vo.setLists(lists);
        return vo;
    }

    @Override
    public TaskVO create(TaskSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "任务标题不能为空");
        }
        SpaceTask task = new SpaceTask();
        task.setUserId(userId);
        task.setPriority(0);
        task.setDone(0);
        task.setSubtasks("[]");
        task.setNote("");
        apply(task, req, userId);
        taskMapper.insert(task);
        return toVO(task);
    }

    @Override
    public TaskVO update(Long id, TaskSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceTask task = requireTask(id, userId);
        if (req.has("title") && !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "任务标题不能为空");
        }
        apply(task, req, userId);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return toVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskCompleteVO complete(Long id, boolean done) {
        Long userId = requireUserId();
        SpaceTask task = requireTask(id, userId);
        task.setDone(done ? 1 : 0);
        task.setDoneTime(done ? LocalDateTime.now() : null);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        SpaceTask next = null;
        RepeatRule rule = readRepeat(task.getRepeatRule());
        if (done && rule != null) {
            LocalDate date = RepeatRules.next(rule, task.getDueDate() != null ? task.getDueDate() : LocalDate.now());
            // 撤销后再完成：下一次已经生成过就不再生成
            boolean exists = taskMapper.selectCount(
                    new LambdaQueryWrapper<SpaceTask>()
                            .eq(SpaceTask::getUserId, userId)
                            .eq(SpaceTask::getDone, 0)
                            .eq(SpaceTask::getTitle, task.getTitle())
                            .eq(SpaceTask::getDueDate, date)
                            .eq(task.getListId() != null, SpaceTask::getListId, task.getListId())
                            .isNull(task.getListId() == null, SpaceTask::getListId)
            ) > 0;
            if (!exists) {
                next = nextOccurrence(task, date);
                taskMapper.insert(next);
            }
        }
        return new TaskCompleteVO(toVO(task), next == null ? null : toVO(next));
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        SpaceTask task = requireTask(id, userId);
        taskMapper.deleteById(task.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 把请求里出现的字段写到实体上；出现但为 null 的字段清空
     */
    private void apply(SpaceTask task, TaskSaveRequest req, Long userId) {
        if (req.has("title")) {
            task.setTitle(req.getTitle().replaceAll("\\s+", " ").trim());
        }
        if (req.has("listId")) {
            if (req.getListId() != null) {
                listService.requireOwned(req.getListId(), userId);
            }
            task.setListId(req.getListId());
        }
        if (req.has("dueDate")) {
            task.setDueDate(req.getDueDate());
        }
        if (req.has("dueTime")) {
            task.setDueTime(StringUtils.hasText(req.getDueTime()) ? req.getDueTime() : null);
        }
        if (req.has("priority")) {
            task.setPriority(req.getPriority() == null ? 0 : req.getPriority());
        }
        if (req.has("estimateMin")) {
            task.setEstimateMin(req.getEstimateMin());
        }
        if (req.has("remindBefore")) {
            task.setRemindBefore(req.getRemindBefore());
        }
        if (req.has("repeat")) {
            task.setRepeatRule(req.getRepeat() == null ? null : writeJson(req.getRepeat()));
        }
        if (req.has("subtasks")) {
            task.setSubtasks(writeJson(req.getSubtasks() == null ? List.of() : req.getSubtasks()));
        }
        if (req.has("source")) {
            TaskSource source = req.getSource();
            task.setSourceType(source == null ? null : source.getType());
            task.setSourceId(source == null ? null : source.getId());
            task.setSourceLabel(source == null ? null : source.getLabel());
        }
        if (req.has("note")) {
            task.setNote(req.getNote() == null ? "" : req.getNote());
        }
    }

    /**
     * 重复任务的下一次：沿用原任务内容，日期换成下一次，子任务全部重置为未完成
     */
    private SpaceTask nextOccurrence(SpaceTask task, LocalDate date) {
        SpaceTask next = new SpaceTask();
        next.setUserId(task.getUserId());
        next.setListId(task.getListId());
        next.setTitle(task.getTitle());
        next.setDueDate(date);
        next.setDueTime(task.getDueTime());
        next.setPriority(task.getPriority());
        next.setDone(0);
        next.setEstimateMin(task.getEstimateMin());
        next.setRemindBefore(task.getRemindBefore());
        next.setRepeatRule(task.getRepeatRule());
        List<SubTask> subtasks = readSubtasks(task.getSubtasks());
        subtasks.forEach(s -> s.setDone(false));
        next.setSubtasks(writeJson(subtasks));
        next.setSourceType(task.getSourceType());
        next.setSourceId(task.getSourceId());
        next.setSourceLabel(task.getSourceLabel());
        next.setNote(task.getNote());
        return next;
    }

    private SpaceTask requireTask(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "任务ID不能为空");
        }
        SpaceTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<SpaceTask>()
                        .eq(SpaceTask::getId, id)
                        .eq(SpaceTask::getUserId, userId)
        );
        if (task == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "任务不存在或已删除");
        }
        return task;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private static RepeatRule readRepeat(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.readValue(json, RepeatRule.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static List<SubTask> readSubtasks(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, SUBTASK_LIST);
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "数据格式不正确");
        }
    }

    private TaskVO toVO(SpaceTask task) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setListId(task.getListId());
        vo.setDueDate(task.getDueDate());
        vo.setDueTime(task.getDueTime());
        vo.setPriority(task.getPriority() == null ? 0 : task.getPriority());
        vo.setDone(Objects.equals(task.getDone(), 1));
        vo.setDoneTime(task.getDoneTime());
        vo.setEstimateMin(task.getEstimateMin());
        vo.setRemindBefore(task.getRemindBefore());
        vo.setRepeat(readRepeat(task.getRepeatRule()));
        vo.setSubtasks(readSubtasks(task.getSubtasks()));
        if (StringUtils.hasText(task.getSourceType())) {
            TaskSource source = new TaskSource();
            source.setType(task.getSourceType());
            source.setId(task.getSourceId());
            source.setLabel(task.getSourceLabel());
            vo.setSource(source);
        }
        vo.setNote(task.getNote() == null ? "" : task.getNote());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }
}
