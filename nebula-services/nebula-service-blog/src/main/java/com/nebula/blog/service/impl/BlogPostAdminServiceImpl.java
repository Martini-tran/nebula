package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.PostAdminPageQuery;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostStatusUpdateRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
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
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.vo.admin.PostAdminVO;
import com.nebula.blog.vo.front.CategorySummaryVO;
import com.nebula.blog.vo.front.TagSummaryVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台文章管理服务实现
 */
@Service
@RequiredArgsConstructor
public class BlogPostAdminServiceImpl implements BlogPostAdminService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String SOURCE_MANUAL = "manual";
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int SLUG_MAX_LENGTH = 220;
    private static final int SUMMARY_MAX_LENGTH = 500;

    private static final Set<String> POST_STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_ARCHIVED);
    private static final Set<String> POST_VISIBILITIES = Set.of(VISIBILITY_PUBLIC, "private");
    private static final Set<String> POST_SOURCE_TYPES = Set.of(SOURCE_MANUAL, "ai", "import");

    private final BlogPostMapper postMapper;
    private final BlogPostCategoryMapper postCategoryMapper;
    private final BlogPostTagMapper postTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    /**
     * 按条件分页查询文章列表
     * 这里会同时处理分类和标签过滤，避免前端分页后再做二次过滤。
     */
    @Override
    public PageResult<PostAdminVO> page(PostAdminPageQuery query) {
        PostAdminPageQuery safeQuery = query == null ? new PostAdminPageQuery() : query;
        List<Long> relationPostIds = findPostIdsByRelations(safeQuery.getCategoryId(), safeQuery.getTagId());
        if (relationPostIds != null && relationPostIds.isEmpty()) {
            return PageResult.empty(safeQuery.safePageNum(), safeQuery.safePageSize());
        }

        Page<BlogPost> page = new Page<>(safeQuery.safePageNum(), safeQuery.safePageSize());
        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(safeQuery.getKeyword()), w -> w
                        .like(BlogPost::getTitle, safeQuery.getKeyword())
                        .or()
                        .like(BlogPost::getSummary, safeQuery.getKeyword())
                        .or()
                        .like(BlogPost::getSlug, safeQuery.getKeyword()))
                .eq(StringUtils.hasText(safeQuery.getStatus()), BlogPost::getStatus, safeQuery.getStatus())
                .eq(StringUtils.hasText(safeQuery.getVisibility()), BlogPost::getVisibility, safeQuery.getVisibility())
                .eq(StringUtils.hasText(safeQuery.getSourceType()), BlogPost::getSourceType, safeQuery.getSourceType())
                .eq(safeQuery.getAuthorId() != null, BlogPost::getAuthorId, safeQuery.getAuthorId())
                .in(relationPostIds != null, BlogPost::getId, relationPostIds)
                .orderByDesc(BlogPost::getCreateTime);

        Page<BlogPost> result = postMapper.selectPage(page, wrapper);
        List<PostAdminVO> rows = result.getRecords().stream()
                .map(this::toAdminVO)
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PostAdminVO detail(Long id) {
        return toAdminVO(requirePost(id));
    }

    /**
     * 新建文章，并同步分类/标签关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PostCreateRequest req) {
        validateCreateRequest(req);
        checkSlugUnique(req.getSlug(), null);

        String status = normalizeValue(req.getStatus(), STATUS_DRAFT, POST_STATUSES, "status");
        BlogPost post = new BlogPost();
        post.setAuthorId(resolveCurrentUserId());
        post.setTitle(req.getTitle());
        post.setSlug(req.getSlug());
        post.setSummary(req.getSummary());
        post.setContentFileId(req.getContentFileId());
        post.setCoverFileId(req.getCoverFileId());
        post.setStatus(status);
        post.setVisibility(normalizeValue(req.getVisibility(), VISIBILITY_PUBLIC, POST_VISIBILITIES, "visibility"));
        post.setSourceType(normalizeValue(req.getSourceType(), SOURCE_MANUAL, POST_SOURCE_TYPES, "sourceType"));
        post.setIsOriginal(req.getIsOriginal() == null ? Boolean.TRUE : req.getIsOriginal());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setPublishedAt(resolvePublishedAt(status, req.getPublishedAt(), null));

        postMapper.insert(post);
        replaceCategories(post.getId(), req.getCategoryIds());
        replaceTags(post.getId(), req.getTagIds());
        return post.getId();
    }

    /**
     * 更新文章基础信息和关联关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PostUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        BlogPost post = requirePost(id);

        if (StringUtils.hasText(req.getTitle())) {
            checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
            post.setTitle(req.getTitle());
        }
        if (StringUtils.hasText(req.getSlug()) && !req.getSlug().equals(post.getSlug())) {
            checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
            checkSlugUnique(req.getSlug(), id);
            post.setSlug(req.getSlug());
        }
        if (req.getSummary() != null) {
            checkLength(req.getSummary(), SUMMARY_MAX_LENGTH, "summary");
            post.setSummary(req.getSummary());
        }
        if (req.getContentFileId() != null) {
            validateFileExists(req.getContentFileId(), "contentFileId");
            post.setContentFileId(req.getContentFileId());
        }
        if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId(), "coverFileId");
            post.setCoverFileId(req.getCoverFileId());
        }
        if (StringUtils.hasText(req.getVisibility())) {
            post.setVisibility(normalizeValue(req.getVisibility(), null, POST_VISIBILITIES, "visibility"));
        }
        if (StringUtils.hasText(req.getSourceType())) {
            post.setSourceType(normalizeValue(req.getSourceType(), null, POST_SOURCE_TYPES, "sourceType"));
        }
        if (req.getIsOriginal() != null) {
            post.setIsOriginal(req.getIsOriginal());
        }
        if (req.getPublishedAt() != null) {
            post.setPublishedAt(req.getPublishedAt());
        }
        if (StringUtils.hasText(req.getStatus())) {
            String status = normalizeValue(req.getStatus(), null, POST_STATUSES, "status");
            post.setStatus(status);
            post.setPublishedAt(resolvePublishedAt(status, req.getPublishedAt(), post.getPublishedAt()));
        }

        postMapper.updateById(post);
        if (req.getCategoryIds() != null) {
            replaceCategories(id, req.getCategoryIds());
        }
        if (req.getTagIds() != null) {
            replaceTags(id, req.getTagIds());
        }
    }

    /**
     * 删除文章时顺带清理文章-分类、文章-标签关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requirePost(id);
        List<Long> oldTagIds = fetchTagIds(id);

        postCategoryMapper.delete(new LambdaQueryWrapper<BlogPostCategory>().eq(BlogPostCategory::getPostId, id));
        postTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, id));
        postMapper.deleteById(id);
        refreshTagUseCounts(oldTagIds);
    }

    /**
     * 单独更新文章状态
     */
    @Override
    public void updateStatus(Long id, PostStatusUpdateRequest req) {
        if (req == null || !StringUtils.hasText(req.getStatus())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文章状态不能为空");
        }
        BlogPost post = requirePost(id);
        String status = normalizeValue(req.getStatus(), null, POST_STATUSES, "status");
        post.setStatus(status);
        post.setPublishedAt(resolvePublishedAt(status, req.getPublishedAt(), post.getPublishedAt()));
        postMapper.updateById(post);
    }

    /**
     * 创建前的完整校验
     */
    private void validateCreateRequest(PostCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文章标题不能为空");
        }
        if (!StringUtils.hasText(req.getSlug())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文章 slug 不能为空");
        }
        if (req.getContentFileId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "正文文件不能为空");
        }
        checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
        checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
        checkLength(req.getSummary(), SUMMARY_MAX_LENGTH, "summary");
        validateFileExists(req.getContentFileId(), "contentFileId");
        validateFileExists(req.getCoverFileId(), "coverFileId");
        normalizeValue(req.getStatus(), STATUS_DRAFT, POST_STATUSES, "status");
        normalizeValue(req.getVisibility(), VISIBILITY_PUBLIC, POST_VISIBILITIES, "visibility");
        normalizeValue(req.getSourceType(), SOURCE_MANUAL, POST_SOURCE_TYPES, "sourceType");
    }

    /**
     * 校验文章是否存在
     */
    private BlogPost requirePost(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文章ID不能为空");
        }
        BlogPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "文章不存在");
        }
        return post;
    }

    /**
     * 作者为空时，默认使用当前登录用户
     */
    private Long resolveCurrentUserId() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "作者不能为空");
        }
        return currentUserId;
    }

    /**
     * 发布状态的文章，如果没有发布时间则自动补当前时间
     */
    private LocalDateTime resolvePublishedAt(String status, LocalDateTime requested, LocalDateTime current) {
        if (requested != null) {
            return requested;
        }
        if (STATUS_PUBLISHED.equals(status) && current == null) {
            return LocalDateTime.now();
        }
        return current;
    }

    /**
     * 统一校验枚举型字符串参数
     */
    private String normalizeValue(String value, String fallback, Set<String> allowed, String fieldName) {
        String normalized = StringUtils.hasText(value) ? value : fallback;
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 不能为空");
        }
        if (!allowed.contains(normalized)) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 非法");
        }
        return normalized;
    }

    /**
     * 校验文本长度
     */
    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    /**
     * 校验 slug 唯一
     */
    private void checkSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(BlogPost::getId, excludeId);
        }
        if (postMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "slug 已存在");
        }
    }

    /**
     * 校验文件资源是否存在
     */
    private void validateFileExists(Long fileId, String fieldName) {
        if (fileId != null && fileAssetMapper.selectById(fileId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 不存在");
        }
    }

    /**
     * 替换文章分类关系
     */
    private void replaceCategories(Long postId, List<Long> categoryIds) {
        List<Long> ids = cleanIds(categoryIds);
        validateCategoriesExist(ids);
        postCategoryMapper.delete(new LambdaQueryWrapper<BlogPostCategory>().eq(BlogPostCategory::getPostId, postId));
        for (Long categoryId : ids) {
            BlogPostCategory relation = new BlogPostCategory();
            relation.setPostId(postId);
            relation.setCategoryId(categoryId);
            postCategoryMapper.insert(relation);
        }
    }

    /**
     * 替换文章标签关系，并刷新标签使用次数
     */
    private void replaceTags(Long postId, List<Long> tagIds) {
        List<Long> oldTagIds = fetchTagIds(postId);
        List<Long> ids = cleanIds(tagIds);
        validateTagsExist(ids);

        postTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId));
        for (Long tagId : ids) {
            BlogPostTag relation = new BlogPostTag();
            relation.setPostId(postId);
            relation.setTagId(tagId);
            postTagMapper.insert(relation);
        }

        Set<Long> changedTagIds = new HashSet<>(oldTagIds);
        changedTagIds.addAll(ids);
        refreshTagUseCounts(changedTagIds);
    }

    /**
     * 清洗ID列表，去掉空值和重复项
     */
    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    /**
     * 校验分类是否都存在
     */
    private void validateCategoriesExist(List<Long> ids) {
        if (!ids.isEmpty() && categoryMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "分类不存在");
        }
    }

    /**
     * 校验标签是否都存在
     */
    private void validateTagsExist(List<Long> ids) {
        if (!ids.isEmpty() && tagMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签不存在");
        }
    }

    /**
     * 重新统计标签使用次数
     */
    private void refreshTagUseCounts(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagId == null) {
                continue;
            }
            Long count = postTagMapper.selectCount(
                    new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getTagId, tagId)
            );
            BlogTag tag = new BlogTag();
            tag.setId(tagId);
            tag.setUseCount(count == null ? 0 : count.intValue());
            tagMapper.updateById(tag);
        }
    }

    /**
     * 根据文章的分类/标签过滤出文章ID集合
     */
    private List<Long> findPostIdsByRelations(Long categoryId, Long tagId) {
        Set<Long> postIds = null;
        if (categoryId != null) {
            postIds = fetchPostIdsByCategory(categoryId);
        }
        if (tagId != null) {
            Set<Long> tagPostIds = fetchPostIdsByTag(tagId);
            if (postIds == null) {
                postIds = tagPostIds;
            } else {
                postIds.retainAll(tagPostIds);
            }
        }
        return postIds == null ? null : new ArrayList<>(postIds);
    }

    /**
     * 查找指定分类下的文章ID
     */
    private Set<Long> fetchPostIdsByCategory(Long categoryId) {
        return postCategoryMapper.selectList(
                        new LambdaQueryWrapper<BlogPostCategory>().eq(BlogPostCategory::getCategoryId, categoryId)
                ).stream()
                .map(BlogPostCategory::getPostId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 查找指定标签下的文章ID
     */
    private Set<Long> fetchPostIdsByTag(Long tagId) {
        return postTagMapper.selectList(
                        new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getTagId, tagId)
                ).stream()
                .map(BlogPostTag::getPostId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 查找文章关联的标签ID
     */
    private List<Long> fetchTagIds(Long postId) {
        return postTagMapper.selectList(
                        new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId)
                ).stream()
                .map(BlogPostTag::getTagId)
                .toList();
    }

    /**
     * 组装文章关联分类
     */
    private List<CategorySummaryVO> fetchCategories(Long postId) {
        List<BlogPostCategory> relations = postCategoryMapper.selectList(
                new LambdaQueryWrapper<BlogPostCategory>().eq(BlogPostCategory::getPostId, postId)
        );
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = relations.stream().map(BlogPostCategory::getCategoryId).toList();
        return categoryMapper.selectBatchIds(ids).stream()
                .map(this::toCategorySummaryVO)
                .toList();
    }

    /**
     * 组装文章关联标签
     */
    private List<TagSummaryVO> fetchTags(Long postId) {
        List<BlogPostTag> relations = postTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId)
        );
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = relations.stream().map(BlogPostTag::getTagId).toList();
        return tagMapper.selectBatchIds(ids).stream()
                .map(this::toTagSummaryVO)
                .toList();
    }

    /**
     * 分类实体转摘要 VO
     */
    private CategorySummaryVO toCategorySummaryVO(BlogCategory category) {
        CategorySummaryVO vo = new CategorySummaryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        return vo;
    }

    /**
     * 标签实体转摘要 VO
     */
    private TagSummaryVO toTagSummaryVO(BlogTag tag) {
        TagSummaryVO vo = new TagSummaryVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        return vo;
    }

    /**
     * 文章实体转后台 VO
     */
    private PostAdminVO toAdminVO(BlogPost post) {
        PostAdminVO vo = new PostAdminVO();
        vo.setId(post.getId());
        vo.setAuthorId(post.getAuthorId());
        vo.setTitle(post.getTitle());
        vo.setSlug(post.getSlug());
        vo.setSummary(post.getSummary());
        vo.setContentFileId(post.getContentFileId());
        vo.setCoverFileId(post.getCoverFileId());
        vo.setStatus(post.getStatus());
        vo.setVisibility(post.getVisibility());
        vo.setSourceType(post.getSourceType());
        vo.setIsOriginal(post.getIsOriginal());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setPublishedAt(post.getPublishedAt());
        vo.setCreateTime(post.getCreateTime());
        vo.setUpdateTime(post.getUpdateTime());

        BlogFileAsset content = findFileAsset(post.getContentFileId());
        if (content != null) {
            vo.setContentUrl(content.getUrl());
        }
        BlogFileAsset cover = findFileAsset(post.getCoverFileId());
        if (cover != null) {
            vo.setCoverUrl(cover.getUrl());
        }

        vo.setCategories(fetchCategories(post.getId()));
        vo.setTags(fetchTags(post.getId()));
        return vo;
    }

    /**
     * 文件资源查询
     */
    private BlogFileAsset findFileAsset(Long fileId) {
        return fileId == null ? null : fileAssetMapper.selectById(fileId);
    }
}
