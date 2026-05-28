package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogSeries;
import com.nebula.blog.entity.BlogSeriesCatalog;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogPostMapper;
import com.nebula.blog.mapper.BlogSeriesMapper;
import com.nebula.blog.service.BlogSeriesCatalogFrontService;
import com.nebula.blog.vo.front.SeriesCatalogNodeVO;
import com.nebula.blog.vo.front.SeriesChapterVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系列目录前台服务实现
 */
@Service
@RequiredArgsConstructor
public class BlogSeriesCatalogFrontServiceImpl implements BlogSeriesCatalogFrontService {

    private static final String STATUS_PUBLISHED = "published";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final int CATALOG_NODE_TYPE_POSTS = 1;

    private final BlogSeriesMapper seriesMapper;
    private final BlogSeriesCatalogMapper catalogMapper;
    private final BlogSeriesCatalogPostMapper catalogPostMapper;
    private final BlogPostMapper postMapper;

    @Override
    public List<SeriesCatalogNodeVO> getCatalogTree(Long seriesId) {
        if (seriesId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "系列ID不能为空");
        }
        BlogSeries series = seriesMapper.selectById(seriesId);
        if (series == null
                || !STATUS_PUBLISHED.equals(series.getStatus())
                || !VISIBILITY_PUBLIC.equals(series.getVisibility())) {
            return List.of();
        }

        List<BlogSeriesCatalog> catalogs = catalogMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalog>()
                        .eq(BlogSeriesCatalog::getSeriesId, seriesId)
                        .orderByAsc(BlogSeriesCatalog::getSortOrder)
                        .orderByAsc(BlogSeriesCatalog::getId));
        if (catalogs.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SeriesChapterVO>> chaptersByCatalog = loadChaptersByCatalog(catalogs);
        return buildTree(catalogs, 0L, chaptersByCatalog);
    }

    @Override
    public List<SeriesChapterVO> listPostsByCatalog(Long catalogId) {
        if (catalogId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目录ID不能为空");
        }
        BlogSeriesCatalog catalog = catalogMapper.selectById(catalogId);
        if (catalog == null) {
            return List.of();
        }
        BlogSeries series = seriesMapper.selectById(catalog.getSeriesId());
        if (series == null
                || !STATUS_PUBLISHED.equals(series.getStatus())
                || !VISIBILITY_PUBLIC.equals(series.getVisibility())) {
            return List.of();
        }
        return loadChaptersByCatalog(List.of(catalog)).getOrDefault(catalogId, List.of());
    }

    private Map<Long, List<SeriesChapterVO>> loadChaptersByCatalog(List<BlogSeriesCatalog> catalogs) {
        Map<Long, BlogSeriesCatalog> catalogMap = catalogs.stream()
                .collect(Collectors.toMap(BlogSeriesCatalog::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        if (catalogMap.isEmpty()) {
            return Map.of();
        }
        List<BlogSeriesCatalogPost> rels = catalogPostMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                        .in(BlogSeriesCatalogPost::getCatalogId, catalogMap.keySet())
                        .orderByAsc(BlogSeriesCatalogPost::getSortOrder)
                        .orderByAsc(BlogSeriesCatalogPost::getId));
        if (rels.isEmpty()) {
            return Map.of();
        }
        Set<Long> postIds = rels.stream().map(BlogSeriesCatalogPost::getPostId).collect(Collectors.toSet());
        Map<Long, BlogPost> postMap = postMapper.selectBatchIds(postIds).stream()
                .filter(p -> STATUS_PUBLISHED.equals(p.getStatus()) && VISIBILITY_PUBLIC.equals(p.getVisibility()))
                .collect(Collectors.toMap(BlogPost::getId, p -> p));
        if (postMap.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<SeriesChapterVO>> result = new HashMap<>();
        int order = 0;
        for (BlogSeriesCatalog catalog : catalogMap.values()) {
            List<SeriesChapterVO> chapters = new ArrayList<>();
            for (BlogSeriesCatalogPost rel : rels) {
                if (!catalog.getId().equals(rel.getCatalogId())) continue;
                BlogPost post = postMap.get(rel.getPostId());
                if (post == null) continue;
                SeriesChapterVO vo = new SeriesChapterVO();
                vo.setPostId(post.getId());
                vo.setTitle(post.getTitle());
                vo.setSlug(post.getSlug());
                vo.setSummary(post.getSummary());
                vo.setStatus(post.getStatus());
                vo.setCatalogId(catalog.getId());
                vo.setCatalogTitle(catalog.getTitle());
                vo.setIsPrimary(Boolean.TRUE.equals(rel.getIsPrimary()));
                vo.setOrder(++order);
                vo.setPublishedAt(post.getPublishedAt());
                chapters.add(vo);
            }
            if (!chapters.isEmpty()) {
                result.put(catalog.getId(), chapters);
            }
        }
        return result;
    }

    private List<SeriesCatalogNodeVO> buildTree(List<BlogSeriesCatalog> catalogs, long parentId,
                                                Map<Long, List<SeriesChapterVO>> chaptersByCatalog) {
        List<BlogSeriesCatalog> children = catalogs.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == parentId)
                .sorted(Comparator
                        .comparing(BlogSeriesCatalog::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BlogSeriesCatalog::getId))
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
            if (Integer.valueOf(CATALOG_NODE_TYPE_POSTS).equals(c.getNodeType())) {
                vo.setPosts(chaptersByCatalog.getOrDefault(c.getId(), List.of()));
            }
            vo.setChildren(buildTree(catalogs, c.getId(), chaptersByCatalog));
            result.add(vo);
        }
        return result;
    }
}
