package com.nebula.space.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;

/**
 * 后台书签导入任务管理服务（仅读 + 取消）
 * 实际导入处理由后台 worker 完成，此处不直接执行解析/写入
 */
public interface SpaceBookmarkImportTaskAdminService {

    /**
     * 分页查询导入任务
     */
    PageResult<BookmarkImportTaskAdminVO> page(BookmarkTaskPageQuery query);

    /**
     * 查询任务详情
     */
    BookmarkImportTaskAdminVO detail(Long id);

    /**
     * 取消任务（仅待处理状态可取消，置为失败）
     */
    void cancel(Long id);
}
