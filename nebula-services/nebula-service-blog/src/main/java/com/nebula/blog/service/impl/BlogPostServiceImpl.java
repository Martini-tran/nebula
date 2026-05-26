package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.front.PostPageQuery;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogPostCategory;
import com.nebula.blog.entity.BlogPostTag;
import com.nebula.blog.entity.BlogTag;
import com.nebula.blog.mapper.BlogCategoryMapper;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.BlogPostCategoryMapper;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogPostTagMapper;
import com.nebula.blog.mapper.BlogTagMapper;
import com.nebula.blog.service.BlogPostService;
import com.nebula.blog.vo.front.CategorySummaryVO;
import com.nebula.blog.vo.front.PostContentVO;
import com.nebula.blog.vo.front.PostListResponse;
import com.nebula.blog.vo.front.PostListVO;
import com.nebula.blog.vo.front.TagSummaryVO;
import com.nebula.common.meilisearch.api.MeiliSearchQuery;
import com.nebula.common.meilisearch.api.MeiliSearchResult;
import com.nebula.common.meilisearch.api.MeilisearchService;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 博客文章前台服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogPostServiceImpl implements BlogPostService {

    private final BlogPostMapper postMapper;
    private final BlogPostCategoryMapper postCategoryMapper;
    private final BlogPostTagMapper postTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogFileAssetMapper fileAssetMapper;
    private final MeilisearchService meilisearchService;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Value("${nebula.meilisearch.indexes.nebula_blog_posts.uid:nebula_blog_posts}")
    private String postIndexUid;

    /**
     * 分页查询文章列表（基于游标分页）
     */
    @Override
    public PostListResponse getArticles(PostPageQuery query) {
        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getStatus, "published")
                .eq(BlogPost::getVisibility, "public")
                .orderByDesc(BlogPost::getPublishedAt);

        // 关键词搜索
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(BlogPost::getTitle, query.getKeyword())
                    .or().like(BlogPost::getSummary, query.getKeyword()));
        }

        // 分类过滤（子查询）
        if (query.getCategoryId() != null) {
            wrapper.inSql(BlogPost::getId,
                    "SELECT post_id FROM blog_post_category WHERE category_id = " + query.getCategoryId());
        }

        // 标签过滤（子查询）
        if (query.getTagId() != null) {
            wrapper.inSql(BlogPost::getId,
                    "SELECT post_id FROM blog_post_tag WHERE tag_id = " + query.getTagId());
        }

        // 游标分页：cursor 为上一页最后一篇文章 publishedAt 的 epoch 秒数
        if (StringUtils.hasText(query.getCursor())) {
            try {
                long epochSeconds = Long.parseLong(query.getCursor());
                LocalDateTime cursorTime = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC);
                wrapper.lt(BlogPost::getPublishedAt, cursorTime);
            } catch (NumberFormatException e) {
                log.warn("Invalid cursor value: {}", query.getCursor());
            }
        }

        int limit = query.getLimit() != null ? query.getLimit() : 10;
        wrapper.last("LIMIT " + (limit + 1));

        List<BlogPost> posts = postMapper.selectList(wrapper);

        boolean hasMore = posts.size() > limit;
        if (hasMore) {
            posts = posts.subList(0, limit);
        }

        List<PostListVO> items = posts.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            PostListVO last = items.get(items.size() - 1);
            nextCursor = last.getPublishedAt() != null
                    ? String.valueOf(last.getPublishedAt().toEpochSecond(ZoneOffset.UTC))
                    : null;
        }

        return new PostListResponse(items, nextCursor);
    }

    /**
     * 使用 Meilisearch 搜索文章，并回表转换为前台列表 VO。
     */
    @Override
    public PostListResponse searchArticles(PostPageQuery query) {
        PostPageQuery safeQuery = query == null ? new PostPageQuery() : query;
        if (!StringUtils.hasText(safeQuery.getKeyword())) {
            return getArticles(safeQuery);
        }

        int limit = safeLimit(safeQuery.getLimit());
        int offset = parseOffsetCursor(safeQuery.getCursor());

        MeiliSearchQuery searchQuery = new MeiliSearchQuery()
                .setQ(safeQuery.getKeyword().trim())
                .setOffset(offset)
                .setLimit(limit + 1)
                .setFilter(buildSearchFilter(safeQuery))
                .setSort(new String[]{"publishedAt:desc"})
                .setAttributesToRetrieve(new String[]{"id"});

        MeiliSearchResult<Map> result =
                meilisearchService.search(postIndexUid, searchQuery, Map.class);
        List<Map> hits = result.getHits() == null ? List.of() : result.getHits();

        boolean hasMore = hits.size() > limit;
        if (hasMore) {
            hits = hits.subList(0, limit);
        }

        List<Long> postIds = hits.stream()
                .map(hit -> toLong(hit.get("id")))
                .filter(id -> id != null)
                .toList();
        if (postIds.isEmpty()) {
            return new PostListResponse(List.of(), null);
        }

        Map<Long, Integer> orderMap = new java.util.HashMap<>(postIds.size());
        for (int i = 0; i < postIds.size(); i++) {
            orderMap.put(postIds.get(i), i);
        }

        List<PostListVO> items = postMapper.selectBatchIds(postIds).stream()
                .filter(this::isPublicPublished)
                .sorted(Comparator.comparingInt(post -> orderMap.getOrDefault(post.getId(), Integer.MAX_VALUE)))
                .map(this::toListVO)
                .toList();

        String nextCursor = hasMore ? String.valueOf(offset + limit) : null;
        return new PostListResponse(items, nextCursor);
    }

    /**
     * 获取热门文章（按浏览量排序）
     */
    @Override
    public List<PostListVO> getHotArticles(int limit) {
        List<BlogPost> posts = postMapper.selectList(
                new LambdaQueryWrapper<BlogPost>()
                        .eq(BlogPost::getStatus, "published")
                        .eq(BlogPost::getVisibility, "public")
                        .orderByDesc(BlogPost::getViewCount)
                        .last("LIMIT " + limit)
        );
        return posts.stream().map(this::toListVO).collect(Collectors.toList());
    }

    /**
     * 获取文章详情，同时增加浏览量
     */
    @Override
    public PostListVO getArticleDetail(String slug) {
        BlogPost post = postMapper.selectOne(
                new LambdaQueryWrapper<BlogPost>()
                        .eq(BlogPost::getSlug, slug)
                        .eq(BlogPost::getStatus, "published")
                        .eq(BlogPost::getVisibility, "public")
        );
        if (post == null) {
            return null;
        }
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BlogPost>()
                .eq(BlogPost::getId, post.getId())
                .setSql("view_count = view_count + 1"));
        return toListVO(post);
    }

    /**
     * 获取文章正文内容（从 OSS 读取 Markdown 文本）
     */
    @Override
    public PostContentVO getArticleContent(String slug) {
        BlogPost post = postMapper.selectOne(
                new LambdaQueryWrapper<BlogPost>()
                        .eq(BlogPost::getSlug, slug)
                        .eq(BlogPost::getStatus, "published")
                        .eq(BlogPost::getVisibility, "public")
                        .select(BlogPost::getContentFileId)
        );
        if (post == null || post.getContentFileId() == null) {
            return new PostContentVO("");
        }
        BlogFileAsset asset = fileAssetMapper.selectById(post.getContentFileId());
        if (asset == null) {
            return new PostContentVO("");
        }

        // OSS 存储：从 MinIO 读取实际 Markdown 内容
        if ("oss".equals(asset.getStorageType())
                && StringUtils.hasText(asset.getBucket())
                && StringUtils.hasText(asset.getObjectKey())) {
            try (InputStream is = ossService.getInputStream(asset.getBucket(), asset.getObjectKey())) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return new PostContentVO(content);
            } catch (Exception e) {
                log.error("Failed to read article content from OSS, objectKey={}", asset.getObjectKey(), e);
                return new PostContentVO("");
            }
        }

        // 兼容旧数据（本地存储或直接 URL 降级）
        return new PostContentVO(asset.getUrl() != null ? asset.getUrl() : "");
    }

    /**
     * 文章实体转列表VO
     */
    private PostListVO toListVO(BlogPost post) {
        PostListVO vo = new PostListVO();
        vo.setId(post.getId());
        vo.setSlug(post.getSlug());
        vo.setTitle(post.getTitle());
        vo.setSummary(post.getSummary());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setPublishedAt(post.getPublishedAt());

        // 封面图使用预签名 URL（私有桶不可直接访问）
        if (post.getCoverFileId() != null) {
            BlogFileAsset cover = fileAssetMapper.selectById(post.getCoverFileId());
            if (cover != null) {
                vo.setCoverUrl(resolveFileUrl(cover));
            }
        }

        vo.setCategories(fetchCategories(post.getId()));
        vo.setTags(fetchTags(post.getId()));
        return vo;
    }

    private boolean isPublicPublished(BlogPost post) {
        return post != null
                && "published".equals(post.getStatus())
                && "public".equals(post.getVisibility());
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private int parseOffsetCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor), 0);
        } catch (NumberFormatException e) {
            log.warn("Invalid search cursor value: {}", cursor);
            return 0;
        }
    }

    private String buildSearchFilter(PostPageQuery query) {
        List<String> filters = new ArrayList<>();
        filters.add("status = \"published\"");
        filters.add("visibility = \"public\"");
        if (query.getCategoryId() != null) {
            filters.add("categoryIds = " + query.getCategoryId());
        }
        if (query.getTagId() != null) {
            filters.add("tagIds = " + query.getTagId());
        }
        return String.join(" AND ", filters);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                log.warn("Invalid search hit id value: {}", str);
            }
        }
        return null;
    }

    /**
     * 解析文件访问 URL：OSS 文件返回预签名 URL，否则返回原始 URL
     */
    private String resolveFileUrl(BlogFileAsset asset) {
        if (asset == null) return null;
        if ("oss".equals(asset.getStorageType())
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

    /**
     * 查询文章关联的分类列表
     */
    private List<CategorySummaryVO> fetchCategories(Long postId) {
        List<BlogPostCategory> relations = postCategoryMapper.selectList(
                new LambdaQueryWrapper<BlogPostCategory>().eq(BlogPostCategory::getPostId, postId)
        );
        if (relations.isEmpty()) return Collections.emptyList();
        List<Long> ids = relations.stream().map(BlogPostCategory::getCategoryId).collect(Collectors.toList());
        return categoryMapper.selectBatchIds(ids).stream().map(c -> {
            CategorySummaryVO v = new CategorySummaryVO();
            v.setId(c.getId());
            v.setName(c.getName());
            v.setSlug(c.getSlug());
            return v;
        }).collect(Collectors.toList());
    }

    /**
     * 查询文章关联的标签列表
     */
    private List<TagSummaryVO> fetchTags(Long postId) {
        List<BlogPostTag> relations = postTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId)
        );
        if (relations.isEmpty()) return Collections.emptyList();
        List<Long> ids = relations.stream().map(BlogPostTag::getTagId).collect(Collectors.toList());
        return tagMapper.selectBatchIds(ids).stream().map(t -> {
            TagSummaryVO v = new TagSummaryVO();
            v.setId(t.getId());
            v.setName(t.getName());
            v.setSlug(t.getSlug());
            return v;
        }).collect(Collectors.toList());
    }
}
