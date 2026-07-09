package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.entity.SpaceBookmarkImportTask;
import com.nebula.space.mapper.SpaceBookmarkImportTaskMapper;
import com.nebula.space.service.SpaceBookmarkImportTaskAdminService;
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台书签导入任务管理实现
 */
@Service
@RequiredArgsConstructor
public class SpaceBookmarkImportTaskAdminServiceImpl implements SpaceBookmarkImportTaskAdminService {

    /** 状态：0待处理 1处理中 2成功 3失败 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_FAIL = 3;

    private final SpaceBookmarkImportTaskMapper taskMapper;

    @Override
    public PageResult<BookmarkImportTaskAdminVO> page(BookmarkTaskPageQuery query) {
        BookmarkTaskPageQuery safe = query == null ? new BookmarkTaskPageQuery() : query;
        Long currentUserId = requireUserId();
        Long filterUserId = UserContext.hasRole("super_admin") ? safe.getUserId() : currentUserId;

        Page<SpaceBookmarkImportTask> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<SpaceBookmarkImportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(filterUserId != null, SpaceBookmarkImportTask::getUserId, filterUserId)
                .eq(safe.getStatus() != null, SpaceBookmarkImportTask::getStatus, safe.getStatus())
                .orderByDesc(SpaceBookmarkImportTask::getCreateTime);
        Page<SpaceBookmarkImportTask> result = taskMapper.selectPage(page, wrapper);
        List<BookmarkImportTaskAdminVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public BookmarkImportTaskAdminVO detail(Long id) {
        return toVO(requireTask(id));
    }

    /**
     * 取消任务
     * 仅待处理状态可取消；处理中/已完成的任务不允许（需 worker 端协作）
     */
    @Override
    public void cancel(Long id) {
        SpaceBookmarkImportTask task = requireTask(id);
        if (task.getStatus() != STATUS_PENDING) {
            throw new BizException(HttpStatus.CONFLICT, "仅待处理状态的任务可取消");
        }
        task.setStatus(STATUS_FAIL);
        task.setErrorMsg("用户取消");
        taskMapper.updateById(task);
    }

    private SpaceBookmarkImportTask requireTask(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "任务ID不能为空");
        }
        SpaceBookmarkImportTask task = taskMapper.selectById(id);
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

    private BookmarkImportTaskAdminVO toVO(SpaceBookmarkImportTask task) {
        BookmarkImportTaskAdminVO vo = new BookmarkImportTaskAdminVO();
        vo.setId(task.getId());
        vo.setUserId(task.getUserId());
        vo.setFileId(task.getFileId());
        vo.setSource(task.getSource());
        vo.setStatus(task.getStatus());
        vo.setTotalCount(task.getTotalCount());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setDuplicateCount(task.getDuplicateCount());
        vo.setFailCount(task.getFailCount());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }
}
