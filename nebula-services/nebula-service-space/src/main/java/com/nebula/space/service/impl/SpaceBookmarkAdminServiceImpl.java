package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.admin.BookmarkAdminPageQuery;
import com.nebula.space.dto.admin.BookmarkBatchDeleteRequest;
import com.nebula.space.dto.admin.BookmarkCreateRequest;
import com.nebula.space.dto.admin.BookmarkMoveRequest;
import com.nebula.space.dto.admin.BookmarkStatusUpdateRequest;
import com.nebula.space.dto.admin.BookmarkTagBindRequest;
import com.nebula.space.dto.admin.BookmarkUpdateRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.entity.SpaceBookmarkTag;
import com.nebula.space.entity.SpaceTag;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceBookmarkTagMapper;
import com.nebula.space.mapper.SpaceTagMapper;
import com.nebula.space.service.SpaceBookmarkAdminService;
import com.nebula.space.vo.admin.BookmarkAdminVO;
import com.nebula.space.vo.admin.SpaceTagAdminVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台书签管理服务实现
 *
 * <p>主要职责：
 * <ol>
 *   <li>书签 CRUD（create-or-find 去重，按 user_id + url_hash）</li>
 *   <li>批量删除、批量移动、状态变更</li>
 *   <li>标签关系全量替换（bindTags）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceBookmarkAdminServiceImpl implements SpaceBookmarkAdminService {

    /** 状态：0正常 1归档 2失效 */
    private static final Set<Integer> STATUS_VALUES = Set.of(0, 1, 2);
    private static final int STATUS_NORMAL = 0;

    /** 来源枚举，越界回退 manual */
    private static final Set<String> SOURCE_VALUES = Set.of("manual", "chrome", "import");
    private static final String SOURCE_DEFAULT = "manual";

    /** 未分类目录约定 ID */
    private static final long UNCATEGORIZED_FOLDER_ID = 0L;

    private final SpaceBookmarkMapper bookmarkMapper;
    private final SpaceBookmarkFolderMapper folderMapper;
    private final SpaceBookmarkTagMapper bookmarkTagMapper;
    private final SpaceTagMapper tagMapper;

    /**
     * 分页查询书签
     * 支持：关键词（标题/URL/描述）、目录、标签、状态、来源、域名
     */
    @Override
    public PageResult<BookmarkAdminVO> page(BookmarkAdminPageQuery query) {
        BookmarkAdminPageQuery safe = query == null ? new BookmarkAdminPageQuery() : query;
        Long currentUserId = requireUserId();
        Long filterUserId = resolveFilterUserId(safe.getUserId(), currentUserId);

        // 标签筛选 → 先取 bookmark_id 列表
        List<Long> bookmarkIdsByTag = null;
        if (safe.getTagId() != null) {
            bookmarkIdsByTag = bookmarkTagMapper.selectList(
                            new LambdaQueryWrapper<SpaceBookmarkTag>().eq(SpaceBookmarkTag::getTagId, safe.getTagId())
                    ).stream()
                    .map(SpaceBookmarkTag::getBookmarkId)
                    .distinct()
                    .toList();
            if (bookmarkIdsByTag.isEmpty()) {
                return PageResult.empty(safe.safePageNum(), safe.safePageSize());
            }
        }

        Page<SpaceBookmark> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<SpaceBookmark> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(filterUserId != null, SpaceBookmark::getUserId, filterUserId)
                .eq(safe.getFolderId() != null, SpaceBookmark::getFolderId, safe.getFolderId())
                .eq(safe.getStatus() != null, SpaceBookmark::getStatus, safe.getStatus())
                .eq(StringUtils.hasText(safe.getSource()), SpaceBookmark::getSource, safe.getSource())
                .eq(StringUtils.hasText(safe.getDomain()), SpaceBookmark::getDomain, safe.getDomain())
                .in(bookmarkIdsByTag != null, SpaceBookmark::getId, bookmarkIdsByTag)
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(SpaceBookmark::getTitle, safe.getKeyword())
                        .or().like(SpaceBookmark::getUrl, safe.getKeyword())
                        .or().like(SpaceBookmark::getDescription, safe.getKeyword()))
                .orderByDesc(SpaceBookmark::getCreateTime);

        Page<SpaceBookmark> result = bookmarkMapper.selectPage(page, wrapper);
        List<BookmarkAdminVO> rows = attachTags(result.getRecords());
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public BookmarkAdminVO detail(Long id) {
        SpaceBookmark bookmark = requireBookmark(id);
        BookmarkAdminVO vo = toVO(bookmark);
        vo.setTags(loadTags(List.of(bookmark.getId())).getOrDefault(bookmark.getId(), Collections.emptyList()));
        return vo;
    }

    /**
     * 创建书签（按 user_id + url_hash 去重）
     * 若已存在，直接返回原书签ID并合并标签关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BookmarkCreateRequest req) {
        Long userId = requireUserId();
        long folderId = req.getFolderId() == null ? UNCATEGORIZED_FOLDER_ID : req.getFolderId();
        if (folderId != UNCATEGORIZED_FOLDER_ID) {
            requireFolderOwned(folderId, userId);
        }

        String normalizedUrl = normalizeUrl(req.getUrl());
        String urlHash = sha256(normalizedUrl);

        SpaceBookmark existing = bookmarkMapper.selectOne(
                new LambdaQueryWrapper<SpaceBookmark>()
                        .eq(SpaceBookmark::getUserId, userId)
                        .eq(SpaceBookmark::getUrlHash, urlHash)
                        .last("limit 1")
        );
        if (existing != null) {
            // 命中重复，合并标签即可
            replaceTags(existing.getId(), req.getTagIds(), userId);
            return existing.getId();
        }

        SpaceBookmark bookmark = new SpaceBookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(folderId);
        bookmark.setTitle(req.getTitle().trim());
        bookmark.setUrl(req.getUrl());
        bookmark.setNormalizedUrl(normalizedUrl);
        bookmark.setUrlHash(urlHash);
        bookmark.setDomain(extractDomain(req.getUrl()));
        bookmark.setDescription(req.getDescription());
        bookmark.setFaviconUrl(req.getFaviconUrl());
        bookmark.setFaviconFileId(req.getFaviconFileId());
        bookmark.setSource(normalizeSource(req.getSource()));
        bookmark.setSourceKey(req.getSourceKey());
        bookmark.setStatus(STATUS_NORMAL);
        bookmark.setVisitCount(0);
        bookmark.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        bookmark.setRemark(req.getRemark());
        bookmarkMapper.insert(bookmark);

        replaceTags(bookmark.getId(), req.getTagIds(), userId);
        return bookmark.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BookmarkUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceBookmark bookmark = requireBookmark(id);

        if (req.getFolderId() != null && !req.getFolderId().equals(bookmark.getFolderId())) {
            if (req.getFolderId() != UNCATEGORIZED_FOLDER_ID) {
                requireFolderOwned(req.getFolderId(), bookmark.getUserId());
            }
            bookmark.setFolderId(req.getFolderId());
        }
        if (StringUtils.hasText(req.getTitle())) {
            bookmark.setTitle(req.getTitle().trim());
        }
        if (StringUtils.hasText(req.getUrl()) && !req.getUrl().equals(bookmark.getUrl())) {
            String normalizedUrl = normalizeUrl(req.getUrl());
            String urlHash = sha256(normalizedUrl);
            // URL 变更时校验：不能与同用户下其它书签重复
            long dupCount = bookmarkMapper.selectCount(
                    new LambdaQueryWrapper<SpaceBookmark>()
                            .eq(SpaceBookmark::getUserId, bookmark.getUserId())
                            .eq(SpaceBookmark::getUrlHash, urlHash)
                            .ne(SpaceBookmark::getId, id)
            );
            if (dupCount > 0) {
                throw new BizException(HttpStatus.CONFLICT, "该URL已存在书签");
            }
            bookmark.setUrl(req.getUrl());
            bookmark.setNormalizedUrl(normalizedUrl);
            bookmark.setUrlHash(urlHash);
            bookmark.setDomain(extractDomain(req.getUrl()));
        }
        if (req.getDescription() != null) {
            bookmark.setDescription(req.getDescription());
        }
        if (req.getFaviconUrl() != null) {
            bookmark.setFaviconUrl(req.getFaviconUrl());
        }
        if (req.getFaviconFileId() != null) {
            bookmark.setFaviconFileId(req.getFaviconFileId());
        }
        if (req.getSortOrder() != null) {
            bookmark.setSortOrder(req.getSortOrder());
        }
        if (req.getRemark() != null) {
            bookmark.setRemark(req.getRemark());
        }
        bookmarkMapper.updateById(bookmark);

        if (req.getTagIds() != null) {
            replaceTags(bookmark.getId(), req.getTagIds(), bookmark.getUserId());
        }
    }

    @Override
    public void updateStatus(Long id, BookmarkStatusUpdateRequest req) {
        if (req == null || !STATUS_VALUES.contains(req.getStatus())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值无效");
        }
        SpaceBookmark bookmark = requireBookmark(id);
        bookmark.setStatus(req.getStatus());
        bookmarkMapper.updateById(bookmark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SpaceBookmark bookmark = requireBookmark(id);
        bookmarkTagMapper.delete(
                new LambdaQueryWrapper<SpaceBookmarkTag>().eq(SpaceBookmarkTag::getBookmarkId, bookmark.getId())
        );
        bookmarkMapper.deleteById(bookmark.getId());
    }

    /**
     * 批量删除
     * 仅删除当前用户拥有的部分（超管除外），返回实际删除条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(BookmarkBatchDeleteRequest req) {
        if (req == null || req.getBookmarkIds() == null || req.getBookmarkIds().isEmpty()) {
            return 0;
        }
        List<SpaceBookmark> ownedList = filterOwned(req.getBookmarkIds());
        if (ownedList.isEmpty()) {
            return 0;
        }
        List<Long> ids = ownedList.stream().map(SpaceBookmark::getId).toList();
        bookmarkTagMapper.delete(
                new LambdaQueryWrapper<SpaceBookmarkTag>().in(SpaceBookmarkTag::getBookmarkId, ids)
        );
        return bookmarkMapper.deleteByIds(ids);
    }

    /**
     * 批量移动书签到目标目录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int move(BookmarkMoveRequest req) {
        if (req == null || req.getBookmarkIds() == null || req.getBookmarkIds().isEmpty()) {
            return 0;
        }
        long targetFolderId = req.getTargetFolderId();
        Long currentUserId = requireUserId();
        if (targetFolderId != UNCATEGORIZED_FOLDER_ID) {
            requireFolderOwned(targetFolderId, currentUserId);
        }
        List<SpaceBookmark> ownedList = filterOwned(req.getBookmarkIds());
        int moved = 0;
        for (SpaceBookmark bookmark : ownedList) {
            bookmark.setFolderId(targetFolderId);
            bookmarkMapper.updateById(bookmark);
            moved++;
        }
        return moved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTags(Long id, BookmarkTagBindRequest req) {
        SpaceBookmark bookmark = requireBookmark(id);
        List<Long> tagIds = req == null ? null : req.getTagIds();
        replaceTags(bookmark.getId(), tagIds, bookmark.getUserId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 全量替换书签的标签关系
     * tagIds 为 null 时不操作；为空列表时清空全部标签
     */
    private void replaceTags(Long bookmarkId, List<Long> tagIds, Long userId) {
        if (tagIds == null) {
            return;
        }
        List<Long> distinctTagIds = tagIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!distinctTagIds.isEmpty()) {
            // 校验所有标签都属于该用户
            long ownedCount = tagMapper.selectCount(
                    new LambdaQueryWrapper<SpaceTag>()
                            .eq(SpaceTag::getUserId, userId)
                            .in(SpaceTag::getId, distinctTagIds)
            );
            if (ownedCount != distinctTagIds.size()) {
                throw new BizException(HttpStatus.BAD_REQUEST, "存在无效或无权访问的标签");
            }
        }

        bookmarkTagMapper.delete(
                new LambdaQueryWrapper<SpaceBookmarkTag>().eq(SpaceBookmarkTag::getBookmarkId, bookmarkId)
        );
        for (Long tagId : distinctTagIds) {
            SpaceBookmarkTag rel = new SpaceBookmarkTag();
            rel.setBookmarkId(bookmarkId);
            rel.setTagId(tagId);
            bookmarkTagMapper.insert(rel);
        }
    }

    /**
     * 校验当前用户对一组书签的所有权
     * 超管可访问任意；普通用户仅命中自己的
     */
    private List<SpaceBookmark> filterOwned(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Long currentUserId = requireUserId();
        boolean superAdmin = UserContext.hasRole("super_admin");
        LambdaQueryWrapper<SpaceBookmark> wrapper = new LambdaQueryWrapper<SpaceBookmark>().in(SpaceBookmark::getId, ids);
        if (!superAdmin) {
            wrapper.eq(SpaceBookmark::getUserId, currentUserId);
        }
        return bookmarkMapper.selectList(wrapper);
    }

    private SpaceBookmark requireBookmark(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "书签ID不能为空");
        }
        SpaceBookmark bookmark = bookmarkMapper.selectById(id);
        if (bookmark == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "书签不存在");
        }
        Long currentUserId = requireUserId();
        if (!UserContext.hasRole("super_admin") && !bookmark.getUserId().equals(currentUserId)) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权操作该书签");
        }
        return bookmark;
    }

    private SpaceBookmarkFolder requireFolderOwned(Long folderId, Long userId) {
        SpaceBookmarkFolder folder = folderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            throw new BizException(HttpStatus.NOT_FOUND, "目录不存在或无权访问");
        }
        return folder;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    /**
     * 普通用户强制按当前用户过滤；超管可指定 userId 跨用户查询
     */
    private Long resolveFilterUserId(Long requestedUserId, Long currentUserId) {
        if (UserContext.hasRole("super_admin")) {
            return requestedUserId;
        }
        return currentUserId;
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_DEFAULT;
        }
        return SOURCE_VALUES.contains(source) ? source : SOURCE_DEFAULT;
    }

    /**
     * URL 规范化：去除前后空白、去除 fragment、host 转小写
     * 仅做轻量规范化，足以支撑大多数场景的去重
     */
    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            StringBuilder sb = new StringBuilder();
            if (!scheme.isEmpty()) {
                sb.append(scheme).append("://");
            }
            sb.append(host);
            if (port > 0) {
                sb.append(":").append(port);
            }
            sb.append(path).append(query);
            return sb.toString();
        } catch (IllegalArgumentException e) {
            // 非合法 URI 时按原值返回，仍可去重相同字面值
            return trimmed;
        }
    }

    private String extractDomain(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论上不会触发；记录后退化为字面值
            log.error("SHA-256 unavailable, fallback to plain text", e);
            return text;
        }
    }

    /**
     * 批量为书签 VO 列表挂载标签
     */
    private List<BookmarkAdminVO> attachTags(List<SpaceBookmark> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = bookmarks.stream().map(SpaceBookmark::getId).toList();
        Map<Long, List<SpaceTagAdminVO>> tagsMap = loadTags(ids);
        return bookmarks.stream().map(b -> {
            BookmarkAdminVO vo = toVO(b);
            vo.setTags(tagsMap.getOrDefault(b.getId(), Collections.emptyList()));
            return vo;
        }).toList();
    }

    /**
     * 批量加载书签关联的标签
     * 通过两次查询（关系表 + 标签表）合并而成，避免 N+1
     */
    private Map<Long, List<SpaceTagAdminVO>> loadTags(List<Long> bookmarkIds) {
        if (bookmarkIds == null || bookmarkIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SpaceBookmarkTag> rels = bookmarkTagMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkTag>().in(SpaceBookmarkTag::getBookmarkId, bookmarkIds)
        );
        if (rels.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> tagIdSet = new HashSet<>();
        for (SpaceBookmarkTag rel : rels) {
            tagIdSet.add(rel.getTagId());
        }
        Map<Long, SpaceTagAdminVO> tagIndex = tagMapper.selectByIds(tagIdSet).stream()
                .collect(Collectors.toMap(SpaceTag::getId, this::toTagVO));

        Map<Long, List<SpaceTagAdminVO>> result = new HashMap<>();
        for (SpaceBookmarkTag rel : rels) {
            SpaceTagAdminVO tagVO = tagIndex.get(rel.getTagId());
            if (tagVO == null) {
                continue;
            }
            result.computeIfAbsent(rel.getBookmarkId(), k -> new ArrayList<>()).add(tagVO);
        }
        return result;
    }

    private BookmarkAdminVO toVO(SpaceBookmark bookmark) {
        BookmarkAdminVO vo = new BookmarkAdminVO();
        vo.setId(bookmark.getId());
        vo.setUserId(bookmark.getUserId());
        vo.setFolderId(bookmark.getFolderId());
        vo.setTitle(bookmark.getTitle());
        vo.setUrl(bookmark.getUrl());
        vo.setNormalizedUrl(bookmark.getNormalizedUrl());
        vo.setUrlHash(bookmark.getUrlHash());
        vo.setDomain(bookmark.getDomain());
        vo.setDescription(bookmark.getDescription());
        vo.setFaviconUrl(bookmark.getFaviconUrl());
        vo.setFaviconFileId(bookmark.getFaviconFileId());
        vo.setSource(bookmark.getSource());
        vo.setSourceKey(bookmark.getSourceKey());
        vo.setStatus(bookmark.getStatus());
        vo.setVisitCount(bookmark.getVisitCount());
        vo.setLastVisitTime(bookmark.getLastVisitTime());
        vo.setSortOrder(bookmark.getSortOrder());
        vo.setRemark(bookmark.getRemark());
        vo.setCreateTime(bookmark.getCreateTime());
        vo.setUpdateTime(bookmark.getUpdateTime());
        return vo;
    }

    private SpaceTagAdminVO toTagVO(SpaceTag tag) {
        SpaceTagAdminVO vo = new SpaceTagAdminVO();
        vo.setId(tag.getId());
        vo.setUserId(tag.getUserId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        vo.setSortOrder(tag.getSortOrder());
        vo.setRemark(tag.getRemark());
        vo.setCreateTime(tag.getCreateTime());
        vo.setUpdateTime(tag.getUpdateTime());
        return vo;
    }
}
