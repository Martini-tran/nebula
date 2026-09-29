package com.nebula.space.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.space.dto.admin.BookmarkAdminPageQuery;
import com.nebula.space.dto.admin.BookmarkBatchDeleteRequest;
import com.nebula.space.dto.admin.BookmarkCreateRequest;
import com.nebula.space.dto.admin.BookmarkMoveRequest;
import com.nebula.space.dto.admin.BookmarkStatusUpdateRequest;
import com.nebula.space.dto.admin.BookmarkTagBindRequest;
import com.nebula.space.dto.admin.BookmarkUpdateRequest;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.vo.admin.BookmarkAdminVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台书签管理服务
 */
public interface SpaceBookmarkAdminService {

    /**
     * 分页查询书签
     */
    PageResult<BookmarkAdminVO> page(BookmarkAdminPageQuery query);

    /**
     * 全局搜索召回：当前用户的正常书签，标题 / 网址 / 描述 / 标签名命中；#xx 按标签名，新收藏的在前
     */
    List<BookmarkAdminVO> search(SearchCriteria q, int limit);

    /**
     * 当前用户在这段时间里收藏的书签（含标签），新的在前
     */
    List<BookmarkAdminVO> createdBetween(LocalDateTime from, LocalDateTime to, int limit);

    /**
     * 查询书签详情（含标签）
     */
    BookmarkAdminVO detail(Long id);

    /**
     * 同一个网址（规范化后相同）已收藏的书签，任意状态；没有返回 null
     *
     * @param excludeId 编辑时排除自己
     */
    BookmarkAdminVO findDuplicate(String url, Long excludeId);

    /**
     * 打开了一次：访问次数 +1、记下访问时间，不算修改书签（更新时间不变）
     */
    void recordVisit(Long id);

    /**
     * 抓网页补全标题与描述：标题还是自动填的域名（或空）时换成网页标题，描述为空时填网页描述；打不开就原样返回
     */
    BookmarkAdminVO fillMeta(Long id);

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
