package com.nebula.space.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;

/**
 * 后台书签导出任务管理服务（仅读 + 取消）
 * 实际导出处理由后台 worker 完成
 */
public interface SpaceBookmarkExportTaskAdminService {

    /**
     * 分页查询导出任务
     */
    PageResult<BookmarkExportTaskAdminVO> page(BookmarkTaskPageQuery query);

    /**
     * 查询任务详情
     */
    BookmarkExportTaskAdminVO detail(Long id);

    /**
     * 取消任务（仅待处理状态可取消，置为失败）
     */
    void cancel(Long id);
}
