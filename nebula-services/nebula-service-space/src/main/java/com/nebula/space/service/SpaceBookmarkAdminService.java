package com.nebula.space.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.space.dto.admin.BookmarkAdminPageQuery;
import com.nebula.space.dto.admin.BookmarkBatchDeleteRequest;
import com.nebula.space.dto.admin.BookmarkCreateRequest;
import com.nebula.space.dto.admin.BookmarkMoveRequest;
import com.nebula.space.dto.admin.BookmarkStatusUpdateRequest;
import com.nebula.space.dto.admin.BookmarkTagBindRequest;
import com.nebula.space.dto.admin.BookmarkUpdateRequest;
import com.nebula.space.vo.admin.BookmarkAdminVO;

/**
 * 后台书签管理服务
 */
public interface SpaceBookmarkAdminService {

    /**
     * 分页查询书签
     */
    PageResult<BookmarkAdminVO> page(BookmarkAdminPageQuery query);

    /**
     * 查询书签详情（含标签）
     */
    BookmarkAdminVO detail(Long id);

    /**
     * 创建书签（同用户内同 URL 视为重复，返回已有书签ID）
     */
    Long create(BookmarkCreateRequest req);

    /**
     * 更新书签
     */
    void update(Long id, BookmarkUpdateRequest req);

    /**
     * 更新书签状态（归档/恢复/失效）
     */
    void updateStatus(Long id, BookmarkStatusUpdateRequest req);

    /**
     * 删除书签
     */
    void delete(Long id);

    /**
     * 批量删除书签
     */
    int batchDelete(BookmarkBatchDeleteRequest req);

    /**
     * 批量移动书签到目标目录
     */
    int move(BookmarkMoveRequest req);

    /**
     * 全量替换书签关联的标签
     */
    void bindTags(Long id, BookmarkTagBindRequest req);
}
