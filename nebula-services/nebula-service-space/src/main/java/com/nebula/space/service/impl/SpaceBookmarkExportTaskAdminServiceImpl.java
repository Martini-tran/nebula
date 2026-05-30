package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.entity.SpaceBookmarkExportTask;
import com.nebula.space.mapper.SpaceBookmarkExportTaskMapper;
import com.nebula.space.service.SpaceBookmarkExportTaskAdminService;
import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台书签导出任务管理实现
 */
@Service
@RequiredArgsConstructor
public class SpaceBookmarkExportTaskAdminServiceImpl implements SpaceBookmarkExportTaskAdminService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_FAIL = 3;

    private final SpaceBookmarkExportTaskMapper taskMapper;

    @Override
    public PageResult<BookmarkExportTaskAdminVO> page(BookmarkTaskPageQuery query) {
        BookmarkTaskPageQuery safe = query == null ? new BookmarkTaskPageQuery() : query;
        Long currentUserId = requireUserId();
        Long filterUserId = UserContext.hasRole("super_admin") ? safe.getUserId() : currentUserId;

        Page<SpaceBookmarkExportTask> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<SpaceBookmarkExportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(filterUserId != null, SpaceBookmarkExportTask::getUserId, filterUserId)
                .eq(safe.getStatus() != null, SpaceBookmarkExportTask::getStatus, safe.getStatus())
                .orderByDesc(SpaceBookmarkExportTask::getCreateTime);
        Page<SpaceBookmarkExportTask> result = taskMapper.selectPage(page, wrapper);
        List<BookmarkExportTaskAdminVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public BookmarkExportTaskAdminVO detail(Long id) {
        return toVO(requireTask(id));
    }

    @Override
    public void cancel(Long id) {
        SpaceBookmarkExportTask task = requireTask(id);
        if (task.getStatus() != STATUS_PENDING) {
            throw new BizException(HttpStatus.CONFLICT, "仅待处理状态的任务可取消");
        }
        task.setStatus(STATUS_FAIL);
        task.setErrorMsg("用户取消");
        taskMapper.updateById(task);
    }

    private SpaceBookmarkExportTask requireTask(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "任务ID不能为空");
        }
        SpaceBookmarkExportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        Long currentUserId = requireUserId();
        if (!UserContext.hasRole("super_admin") && !task.getUserId().equals(currentUserId)) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权操作该任务");
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

    private BookmarkExportTaskAdminVO toVO(SpaceBookmarkExportTask task) {
        BookmarkExportTaskAdminVO vo = new BookmarkExportTaskAdminVO();
        vo.setId(task.getId());
        vo.setUserId(task.getUserId());
        vo.setFileId(task.getFileId());
        vo.setExportType(task.getExportType());
        vo.setScopeType(task.getScopeType());
        vo.setScopeId(task.getScopeId());
        vo.setStatus(task.getStatus());
        vo.setTotalCount(task.getTotalCount());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }
}
