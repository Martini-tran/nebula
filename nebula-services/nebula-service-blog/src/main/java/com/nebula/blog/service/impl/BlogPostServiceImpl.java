package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.front.PostPageQuery;
import com.nebula.blog.entity.BlogCategory;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogPostServiceImpl implements BlogPostService {

    private final BlogPostMapper postMapper;
    private final BlogPostCategoryMapper postCategoryMapper;
    private final BlogPostTagMapper postTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Override
    public PostListResponse getArticles(PostPageQuery query) {
        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getStatus, "published")
                .eq(BlogPost::getVisibility, "public")
                .orderByDesc(BlogPost::getPublishedAt);

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(BlogPost::getTitle, query.getKeyword())
                    .or().like(BlogPost::getSummary, query.getKeyword()));
        }

        // cursor-based pagination: cursor = last post's publishedAt as epoch millis
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
                    ? String.valueOf(last.getPublishedAt().toEpochSecond(java.time.ZoneOffset.UTC))
                    : null;
        }

        return new PostListResponse(items, nextCursor);
    }

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

    @Override
    public PostListVO getArticleDetail(String slug) {
        BlogPost post = postMapper.selectOne(
                new LambdaQueryWrapper<BlogPost>()
                        .eq(BlogPost::getSlug, slug)
                        .eq(BlogPost::getStatus, "published")
        );
        if (post == null) {
            return null;
        }
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BlogPost>()
                .eq(BlogPost::getId, post.getId())
                .setSql("view_count = view_count + 1"));
        return toListVO(post);
    }

    @Override
    public PostContentVO getArticleContent(String slug) {
        BlogPost post = postMapper.selectOne(
                new LambdaQueryWrapper<BlogPost>()
                        .eq(BlogPost::getSlug, slug)
                        .eq(BlogPost::getStatus, "published")
                        .select(BlogPost::getContentFileId)
        );
        if (post == null || post.getContentFileId() == null) {
            return new PostContentVO("");
        }
        BlogFileAsset asset = fileAssetMapper.selectById(post.getContentFileId());
        if (asset == null) {
            return new PostContentVO("");
        }
        // 返回文件访问 URL，由调用方决定如何加载内容
        return new PostContentVO(asset.getUrl());
    }

    private PostListVO toListVO(BlogPost post) {
        PostListVO vo = new PostListVO();
        vo.setId(post.getId());
        vo.setSlug(post.getSlug());
        vo.setTitle(post.getTitle());
        vo.setSummary(post.getSummary());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setPublishedAt(post.getPublishedAt());

        if (post.getCoverFileId() != null) {
            BlogFileAsset cover = fileAssetMapper.selectById(post.getCoverFileId());
            if (cover != null) {
                vo.setCoverUrl(cover.getUrl());
            }
        }

        vo.setCategories(fetchCategories(post.getId()));
        vo.setTags(fetchTags(post.getId()));
        return vo;
    }

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
