package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.front.SeriesPageQuery;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogPostTag;
import com.nebula.blog.entity.BlogSeries;
import com.nebula.blog.entity.BlogSeriesCatalog;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import com.nebula.blog.entity.BlogTag;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogPostTagMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogPostMapper;
import com.nebula.blog.mapper.BlogSeriesMapper;
import com.nebula.blog.mapper.BlogTagMapper;
import com.nebula.blog.service.BlogSeriesService;
import com.nebula.blog.vo.front.SeriesCatalogNodeVO;
import com.nebula.blog.vo.front.SeriesChapterVO;
import com.nebula.blog.vo.front.SeriesDetailVO;
import com.nebula.blog.vo.front.SeriesListResponse;
import com.nebula.blog.vo.front.SeriesListVO;
import com.nebula.blog.vo.front.TagSummaryVO;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客系列前台服务实现
 *
 * <p>列表与详情仅返回 status=published 且 visibility=public 的系列；
 * 详情中的章节也只包含已发布的文章。游标分页基于 sort_order|id 组合，
 * 这样在 sort_order 相同的情况下也能稳定翻页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSeriesServiceImpl implements BlogSeriesService {

    private static final String STATUS_PUBLISHED = "published";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String OSS_STORAGE_TYPE = "oss";
    private static final int NODE_TYPE_POST_GROUP = 1;
    private static final int DEFAULT_LIMIT = 12;
    private static final int MAX_LIMIT = 50;

    private final BlogSeriesMapper seriesMapper;
    private final BlogSeriesCatalogMapper catalogMapper;
    private final BlogSeriesCatalogPostMapper catalogPostMapper;
    private final BlogPostMapper postMapper;
    private final BlogPostTagMapper postTagMapper;
    private final BlogTagMapper tagMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Override
    public SeriesListResponse getSeriesList(SeriesPageQuery query) {
        SeriesPageQuery safe = query == null ? new SeriesPageQuery() : query;
        int limit = safeLimit(safe.getLimit());

        LambdaQueryWrapper<BlogSeries> wrapper = new LambdaQueryWrapper<BlogSeries>()
                .eq(BlogSeries::getStatus, STATUS_PUBLISHED)
                .eq(BlogSeries::getVisibility, VISIBILITY_PUBLIC);
        if (StringUtils.hasText(safe.getKeyword())) {
            wrapper.and(w -> w.like(BlogSeries::getName, safe.getKeyword())
                    .or().like(BlogSeries::getDescription, safe.getKeyword()));
        }
        if (safe.getIsFinished() != null) {
            wrapper.eq(BlogSeries::getIsFinished, safe.getIsFinished());
        }

        // 游标格式: sortOrder|id
        if (StringUtils.hasText(safe.getCursor())) {
            CursorValue cv = parseCursor(safe.getCursor());
            if (cv != null) {
                // 按 sort_order ASC, id DESC 排序，下一页：(sort_order > sv) 或 (= sv 且 id < iv)
                wrapper.and(w -> w.gt(BlogSeries::getSortOrder, cv.sortOrder)
                        .or(or -> or.eq(BlogSeries::getSortOrder, cv.sortOrder)
                                .lt(BlogSeries::getId, cv.id)));
            }
        }

        wrapper.orderByAsc(BlogSeries::getSortOrder)
                .orderByDesc(BlogSeries::getId)
                .last("LIMIT " + (limit + 1));

        List<BlogSeries> rows = seriesMapper.selectList(wrapper);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        // 批量查询每个系列的已发布文章数量与标签集合
        List<Long> seriesIds = rows.stream().map(BlogSeries::getId).toList();
        Map<Long, List<Long>> seriesPostIds = collectPublishedPostIdsBySeries(seriesIds);
        Map<Long, List<TagSummaryVO>> seriesTags = collectTagsForSeries(seriesPostIds);

        List<SeriesListVO> items = rows.stream().map(s -> {
            SeriesListVO vo = new SeriesListVO();
            vo.setId(s.getId());
            vo.setSlug(s.getSlug());
            vo.setName(s.getName());
            vo.setDescription(s.getDescription());
            vo.setIsFinished(s.getIsFinished());
            vo.setSortOrder(s.getSortOrder());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setArticleCount(seriesPostIds.getOrDefault(s.getId(), List.of()).size());
            vo.setTags(seriesTags.getOrDefault(s.getId(), List.of()));
            if (s.getCoverFileId() != null) {
                BlogFileAsset cover = fileAssetMapper.selectById(s.getCoverFileId());
                if (cover != null) {
                    vo.setCoverUrl(resolveFileUrl(cover));
                }
            }
            return vo;
        }).toList();

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            BlogSeries last = rows.get(rows.size() - 1);
            nextCursor = (last.getSortOrder() == null ? 0 : last.getSortOrder()) + "|" + last.getId();
        }
        return new SeriesListResponse(items, nextCursor);
    }

    @Override
    public SeriesDetailVO getSeriesDetail(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        BlogSeries series = seriesMapper.selectOne(new LambdaQueryWrapper<BlogSeries>()
                .eq(BlogSeries::getSlug, slug)
                .eq(BlogSeries::getStatus, STATUS_PUBLISHED)
                .eq(BlogSeries::getVisibility, VISIBILITY_PUBLIC)
                .last("LIMIT 1"));
        if (series == null) {
            return null;
        }

        // 1) 拉所有目录节点
        List<BlogSeriesCatalog> catalogs = catalogMapper.selectList(new LambdaQueryWrapper<BlogSeriesCatalog>()
                .eq(BlogSeriesCatalog::getSeriesId, series.getId())
                .orderByAsc(BlogSeriesCatalog::getSortOrder)
                .orderByAsc(BlogSeriesCatalog::getId));
        Map<Long, BlogSeriesCatalog> catalogById = catalogs.stream()
                .collect(Collectors.toMap(BlogSeriesCatalog::getId, c -> c, (a, b) -> a, LinkedHashMap::new));

        // 2) 拉所有目录-文章关联
        Map<Long, List<SeriesChapterVO>> chaptersByCatalog;
        List<SeriesChapterVO> flatChapters = new ArrayList<>();
        if (!catalogs.isEmpty()) {
            List<Long> catalogIds = new ArrayList<>(catalogById.keySet());
            List<BlogSeriesCatalogPost> rels = catalogPostMapper.selectList(
                    new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                            .in(BlogSeriesCatalogPost::getCatalogId, catalogIds)
                            .orderByAsc(BlogSeriesCatalogPost::getSortOrder)
                            .orderByAsc(BlogSeriesCatalogPost::getId));

            Set<Long> postIds = rels.stream()
                    .map(BlogSeriesCatalogPost::getPostId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<Long, BlogPost> postMap = postIds.isEmpty() ? Map.of() : postMapper.selectBatchIds(postIds).stream()
                    .filter(p -> STATUS_PUBLISHED.equals(p.getStatus()) && VISIBILITY_PUBLIC.equals(p.getVisibility()))
                    .collect(Collectors.toMap(BlogPost::getId, p -> p));

            chaptersByCatalog = new HashMap<>();
            int order = 0;
            // 按目录的 sort_order 顺序遍历，再按 rel 顺序展开为扁平 chapters
            for (BlogSeriesCatalog c : catalogs) {
                List<SeriesChapterVO> chapters = rels.stream()
                        .filter(r -> r.getCatalogId().equals(c.getId()))
                        .map(r -> {
                            BlogPost p = postMap.get(r.getPostId());
                            if (p == null) return null;
                            SeriesChapterVO vo = new SeriesChapterVO();
                            vo.setPostId(p.getId());
                            vo.setTitle(p.getTitle());
                            vo.setSlug(p.getSlug());
                            vo.setSummary(p.getSummary());
                            vo.setStatus(p.getStatus());
                            vo.setCatalogId(c.getId());
                            vo.setCatalogTitle(c.getTitle());
                            vo.setIsPrimary(Boolean.TRUE.equals(r.getIsPrimary()));
                            vo.setPublishedAt(p.getPublishedAt());
                            return vo;
                        })
                        .filter(java.util.Objects::nonNull)
                        .toList();
                chaptersByCatalog.put(c.getId(), chapters);
                for (SeriesChapterVO ch : chapters) {
                    ch.setOrder(++order);
                    flatChapters.add(ch);
                }
            }
        } else {
            chaptersByCatalog = Map.of();
        }

        // 3) 构建目录树
        List<SeriesCatalogNodeVO> tree = buildCatalogTree(catalogs, chaptersByCatalog);

        // 4) 标签聚合
        List<TagSummaryVO> tags = aggregateTags(flatChapters.stream().map(SeriesChapterVO::getPostId).toList());

        SeriesDetailVO vo = new SeriesDetailVO();
        vo.setId(series.getId());
        vo.setSlug(series.getSlug());
        vo.setName(series.getName());
        vo.setDescription(series.getDescription());
        vo.setIsFinished(series.getIsFinished());
        vo.setArticleCount(flatChapters.size());
        vo.setCreateTime(series.getCreateTime());
        vo.setUpdateTime(series.getUpdateTime());
        vo.setTags(tags);
        vo.setChapters(flatChapters);
        vo.setCatalog(tree);

        if (series.getCoverFileId() != null) {
            BlogFileAsset cover = fileAssetMapper.selectById(series.getCoverFileId());
            if (cover != null) {
                vo.setCoverUrl(resolveFileUrl(cover));
            }
        }
        return vo;
    }

    // -------------------- 辅助 --------------------

    /**
     * 一次性拉取多个系列的"已发布且公开"的文章 ID 列表。
     * 用于列表页统计数量，避免 N+1 查询。
     */
    private Map<Long, List<Long>> collectPublishedPostIdsBySeries(List<Long> seriesIds) {
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        // catalog 按 series 分组
        List<BlogSeriesCatalog> catalogs = catalogMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalog>().in(BlogSeriesCatalog::getSeriesId, seriesIds));
        if (catalogs.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> catalogIdToSeries = catalogs.stream()
                .collect(Collectors.toMap(BlogSeriesCatalog::getId, BlogSeriesCatalog::getSeriesId));

        List<BlogSeriesCatalogPost> rels = catalogPostMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                        .in(BlogSeriesCatalogPost::getCatalogId, catalogIdToSeries.keySet()));
        if (rels.isEmpty()) {
            return Map.of();
        }
        Set<Long> postIds = rels.stream().map(BlogSeriesCatalogPost::getPostId).collect(Collectors.toSet());
        Set<Long> publishedIds = postMapper.selectBatchIds(postIds).stream()
                .filter(p -> STATUS_PUBLISHED.equals(p.getStatus()) && VISIBILITY_PUBLIC.equals(p.getVisibility()))
                .map(BlogPost::getId)
                .collect(Collectors.toSet());

        Map<Long, List<Long>> result = new HashMap<>();
        for (BlogSeriesCatalogPost r : rels) {
            if (!publishedIds.contains(r.getPostId())) continue;
            Long seriesId = catalogIdToSeries.get(r.getCatalogId());
            if (seriesId == null) continue;
            result.computeIfAbsent(seriesId, k -> new ArrayList<>()).add(r.getPostId());
        }
        // 去重
        result.replaceAll((k, v) -> new ArrayList<>(new LinkedHashSet<>(v)));
        return result;
    }

    private Map<Long, List<TagSummaryVO>> collectTagsForSeries(Map<Long, List<Long>> seriesPostIds) {
        if (seriesPostIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> allPostIds = seriesPostIds.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        if (allPostIds.isEmpty()) {
            return Map.of();
        }
        List<BlogPostTag> rels = postTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().in(BlogPostTag::getPostId, allPostIds));
        if (rels.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = rels.stream().map(BlogPostTag::getTagId).collect(Collectors.toSet());
        Map<Long, BlogTag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(BlogTag::getId, t -> t));

        // postId -> tagIds
        Map<Long, List<Long>> postToTags = rels.stream()
                .collect(Collectors.groupingBy(BlogPostTag::getPostId,
                        Collectors.mapping(BlogPostTag::getTagId, Collectors.toList())));

        Map<Long, List<TagSummaryVO>> result = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : seriesPostIds.entrySet()) {
            Set<Long> distinct = new LinkedHashSet<>();
            for (Long postId : entry.getValue()) {
                List<Long> ts = postToTags.get(postId);
                if (ts != null) distinct.addAll(ts);
            }
            List<TagSummaryVO> tags = distinct.stream()
                    .map(tagMap::get)
                    .filter(java.util.Objects::nonNull)
                    .map(this::toTagSummary)
                    .toList();
            if (!tags.isEmpty()) {
                result.put(entry.getKey(), tags);
            }
        }
        return result;
    }

    private List<TagSummaryVO> aggregateTags(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        List<BlogPostTag> rels = postTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().in(BlogPostTag::getPostId, postIds));
        if (rels.isEmpty()) {
            return List.of();
        }
        Set<Long> tagIds = rels.stream().map(BlogPostTag::getTagId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, BlogTag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(BlogTag::getId, t -> t));
        return tagIds.stream()
                .map(tagMap::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toTagSummary)
                .toList();
    }

    private TagSummaryVO toTagSummary(BlogTag tag) {
        TagSummaryVO vo = new TagSummaryVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        return vo;
    }

    private List<SeriesCatalogNodeVO> buildCatalogTree(List<BlogSeriesCatalog> catalogs,
                                                       Map<Long, List<SeriesChapterVO>> chaptersByCatalog) {
        if (catalogs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BlogSeriesCatalog>> byParent = catalogs.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        return buildTree(byParent, 0L, chaptersByCatalog);
    }

    private List<SeriesCatalogNodeVO> buildTree(Map<Long, List<BlogSeriesCatalog>> byParent, long parentId,
                                                Map<Long, List<SeriesChapterVO>> chaptersByCatalog) {
        List<BlogSeriesCatalog> children = byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .toList();
        List<SeriesCatalogNodeVO> result = new ArrayList<>();
        for (BlogSeriesCatalog c : children) {
            SeriesCatalogNodeVO vo = new SeriesCatalogNodeVO();
            vo.setId(c.getId());
            vo.setParentId(c.getParentId());
            vo.setTitle(c.getTitle());
            vo.setNodeType(c.getNodeType());
            vo.setLinkUrl(c.getLinkUrl());
            vo.setLinkTarget(c.getLinkTarget());
            vo.setSortOrder(c.getSortOrder());
            if (c.getNodeType() != null && c.getNodeType() == NODE_TYPE_POST_GROUP) {
                vo.setPosts(chaptersByCatalog.getOrDefault(c.getId(), List.of()));
            } else {
                vo.setPosts(Collections.emptyList());
            }
            vo.setChildren(buildTree(byParent, c.getId(), chaptersByCatalog));
            result.add(vo);
        }
        return result;
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 解析 sortOrder|id 形式的游标
     */
    private CursorValue parseCursor(String cursor) {
        int sep = cursor.indexOf('|');
        if (sep <= 0 || sep == cursor.length() - 1) {
            return null;
        }
        try {
            int sortOrder = Integer.parseInt(cursor.substring(0, sep));
            long id = Long.parseLong(cursor.substring(sep + 1));
            return new CursorValue(sortOrder, id);
        } catch (NumberFormatException e) {
            log.warn("Invalid series cursor: {}", cursor);
            return null;
        }
    }

    private String resolveFileUrl(BlogFileAsset asset) {
        if (asset == null) return null;
        if (OSS_STORAGE_TYPE.equals(asset.getStorageType())
                && StringUtils.hasText(asset.getBucket())
                && StringUtils.hasText(asset.getObjectKey())) {
            try {
                return ossService.getPresignedUrl(asset.getBucket(), asset.getObjectKey(), presignedUrlExpirySeconds);
            } catch (Exception e) {
                log.warn("Failed to get presigned URL for objectKey={}, falling back to stored url",
                        asset.getObjectKey(), e);
            }
        }
        return asset.getUrl();
    }

    private record CursorValue(int sortOrder, long id) {
    }
}
