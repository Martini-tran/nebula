package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.TaskListSaveRequest;
import com.nebula.space.entity.SpaceTask;
import com.nebula.space.entity.SpaceTaskList;
import com.nebula.space.mapper.SpaceTaskListMapper;
import com.nebula.space.mapper.SpaceTaskMapper;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.vo.me.TaskListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 任务清单服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceTaskListServiceImpl implements SpaceTaskListService {

    private static final String DEFAULT_COLOR = "#4f46e5";

    private final SpaceTaskListMapper listMapper;
    private final SpaceTaskMapper taskMapper;

    @Override
    public List<TaskListVO> list() {
        Long userId = requireUserId();
        return listMapper.selectList(
                        new LambdaQueryWrapper<SpaceTaskList>()
                                .eq(SpaceTaskList::getUserId, userId)
                                .orderByAsc(SpaceTaskList::getSortOrder)
                                .orderByAsc(SpaceTaskList::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public TaskListVO create(TaskListSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "清单名称不能为空");
        }
        String name = req.getName().trim();
        checkNameUnique(userId, name, null);

        SpaceTaskList list = new SpaceTaskList();
        list.setUserId(userId);
        list.setName(name);
        list.setColor(StringUtils.hasText(req.getColor()) ? req.getColor() : DEFAULT_COLOR);
        list.setSortOrder(0);
        listMapper.insert(list);
        return toVO(list);
    }

    @Override
    public void update(Long id, TaskListSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceTaskList list = requireList(id, userId);
        if (StringUtils.hasText(req.getName()) && !req.getName().trim().equals(list.getName())) {
            checkNameUnique(userId, req.getName().trim(), id);
            list.setName(req.getName().trim());
        }
        if (StringUtils.hasText(req.getColor())) {
            list.setColor(req.getColor());
        }
        listMapper.updateById(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = requireUserId();
        SpaceTaskList list = requireList(id, userId);
        // 清单里的任务回到「无清单」，不跟着删
        taskMapper.update(null, new LambdaUpdateWrapper<SpaceTask>()
                .set(SpaceTask::getListId, null)
                .eq(SpaceTask::getUserId, userId)
                .eq(SpaceTask::getListId, list.getId()));
        listMapper.deleteById(list.getId());
    }

    @Override
    public void requireOwned(Long id, Long userId) {
        requireList(id, userId);
    }

    // ----------------------------------------------------------------- 内部工具

    private void checkNameUnique(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<SpaceTaskList> wrapper = new LambdaQueryWrapper<SpaceTaskList>()
                .eq(SpaceTaskList::getUserId, userId)
                .eq(SpaceTaskList::getName, name)
                .ne(excludeId != null, SpaceTaskList::getId, excludeId);
        if (listMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.CONFLICT, "已有同名清单");
        }
    }

    private SpaceTaskList requireList(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "清单ID不能为空");
        }
        SpaceTaskList list = listMapper.selectOne(
                new LambdaQueryWrapper<SpaceTaskList>()
                        .eq(SpaceTaskList::getId, id)
                        .eq(SpaceTaskList::getUserId, userId)
        );
        if (list == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "清单不存在或已删除");
        }
        return list;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private TaskListVO toVO(SpaceTaskList list) {
        TaskListVO vo = new TaskListVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setColor(list.getColor());
        return vo;
    }
}
