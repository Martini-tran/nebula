package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.PostAdminPageQuery;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostStatusUpdateRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
import com.nebula.blog.entity.BlogCategory;
import com.nebula.blog.entity.BlogContentVersion;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogPostCategory;
import com.nebula.blog.entity.BlogPostTag;
import com.nebula.blog.entity.BlogTag;
import com.nebula.blog.event.PostIndexEvent;
import com.nebula.blog.mapper.BlogCategoryMapper;
import com.nebula.blog.mapper.BlogContentVersionMapper;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.BlogPostCategoryMapper;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogPostTagMapper;
import com.nebula.blog.mapper.BlogTagMapper;
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.vo.admin.PostAdminVO;
import com.nebula.blog.vo.admin.PostSearchDocument;
import com.nebula.blog.vo.front.CategorySummaryVO;
import com.nebula.blog.vo.front.TagSummaryVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台文章管理服务实现
 *
 * <p>主要职责：
 * <ol>
 *   <li>新建文章：将 Markdown 正文上传至 MinIO（OSS），写 DB，若已发布则发布 {@link PostIndexEvent} 触发 Meilisearch 同步</li>
 *   <li>修改文章：先将当前快照写入 blog_content_version，FIFO 清理超限快照（并可选删除旧 OSS 文件），
 *       再上传新内容到 OSS，更新 DB，最后发布索引事件</li>
 *   <li>删除/状态变更：删除或变更后发布索引事件，由 BlogSearchSyncServiceImpl 在事务提交后执行 Meilisearch 操作</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogPostAdminServiceImpl implements BlogPostAdminService {

    // ------------------------------------------------------------------ 常量
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String SOURCE_MANUAL = "manual";
    private static final String OSS_STORAGE_TYPE = "oss";
    private static final String CHANGE_TYPE_MANUAL = "manual";

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int SLUG_MAX_LENGTH = 220;
    private static final int SUMMARY_MAX_LENGTH = 500;
    private static final String MARKDOWN_EXTENSION = ".md";
    private static final String MARKDOWN_FILE_TYPE = "markdown";
    private static final String MARKDOWN_MIME_TYPE = "text/markdown";

    private static final Set<String> POST_STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_ARCHIVED);
    private static final Set<String> POST_VISIBILITIES = Set.of(VISIBILITY_PUBLIC, "private");
    private static final Set<String> POST_SOURCE_TYPES = Set.of(SOURCE_MANUAL, "ai", "import");

    // ------------------------------------------------------------------ 注入：Mapper
    private final BlogPostMapper postMapper;
    private final BlogPostCategoryMapper postCategoryMapper;
    private final BlogPostTagMapper postTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogFileAssetMapper fileAssetMapper;
    private final BlogContentVersionMapper contentVersionMapper;

    // ------------------------------------------------------------------ 注入：外部服务
    /**
     * MinIO 对象存储服务
     * 使用 @Qualifier 避免多实现时注入歧义
     */
    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------ 配置
    /** MinIO 默认 Bucket，对应 nebula.minio.default-bucket */
    @Value("${nebula.minio.default-bucket}")
    private String defaultBucket;

    /** 每篇文章最多保留的快照数量，默认 10 */
    @Value("${blog.post.snapshot.max-count:10}")
    private int maxSnapshotCount;

    /** 清理快照时是否同步删除 OSS 文件，默认 true */
    @Value("${blog.post.snapshot.oss-cleanup:true}")
    private boolean snapshotOssCleanup;

    /**
     * OSS 预签名 URL 有效期（秒）。
     * 私有桶不能直接访问，所有对外暴露的文件 URL 均通过预签名方式生成。
     */
    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    // ================================================================== 公开接口实现

    /**
     * 按条件分页查询文章列表
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
        return toAdminDetailVO(requirePost(id));
    }

    /**
     * 新建文章：
     * <ol>
     *   <li>上传 Markdown 到 MinIO（在事务内，失败抛异常中断事务）</li>
     *   <li>写 blog_post、分类/标签关系</li>
     *   <li>若状态为 published，发布 PostIndexEvent（AFTER_COMMIT 触发 Meilisearch 同步）</li>
     * </ol>
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
        post.setContentFileId(resolveContentFileId(req.getContent(), req.getContentFileId(), req.getSlug()));
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

        // 发布索引事件（AFTER_COMMIT 后由 BlogSearchSyncServiceImpl 消费）
        publishIndexEvent(post);

        return post.getId();
    }

    /**
     * 更新文章：
     * <ol>
     *   <li>将当前文章状态快照写入 blog_content_version，超限则 FIFO 清理旧快照</li>
     *   <li>若正文有更新，上传新 Markdown 到 MinIO</li>
     *   <li>更新 blog_post 及关联关系</li>
     *   <li>发布 PostIndexEvent</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PostUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        BlogPost post = requirePost(id);

        // ★ 修改前：先保存当前状态的快照（changeNote 来自请求参数）
        saveSnapshot(post, req.getChangeNote());

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
        if (StringUtils.hasText(req.getContent())) {
            post.setContentFileId(saveMarkdownContent(req.getContent(), post.getSlug()));
        }
        boolean clearCoverFileId = Boolean.TRUE.equals(req.getClearCoverFileId());
        if (clearCoverFileId) {
            // 前端显式发出"移除封面"信号
            post.setCoverFileId(null);
        } else if (req.getCoverFileId() != null) {
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

        if (clearCoverFileId) {
            LambdaUpdateWrapper<BlogPost> updateWrapper = new LambdaUpdateWrapper<BlogPost>()
                    .eq(BlogPost::getId, id)
                    .set(BlogPost::getCoverFileId, null);
            postMapper.update(post, updateWrapper);
        } else {
            postMapper.updateById(post);
        }
        if (req.getCategoryIds() != null) {
            replaceCategories(id, req.getCategoryIds());
        }
        if (req.getTagIds() != null) {
            replaceTags(id, req.getTagIds());
        }

        // ★ 更新后：发布索引事件
        publishIndexEvent(post);
    }

    /**
     * 删除文章：清理关系表，并在 AFTER_COMMIT 后从 Meilisearch 移除文档
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

        // ★ 删除后：从 Meilisearch 移除
        eventPublisher.publishEvent(new PostIndexEvent(this, id, "delete", null));
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

        // ★ 状态变更后：同步 Meilisearch
        publishIndexEvent(post);
    }

    // ================================================================== 快照相关

    /**
     * 将文章当前状态（修改前）写入 blog_content_version，然后清理超限快照。
     *
     * @param post       修改前的文章实体
     * @param changeNote 变更说明（前端可选填，写入快照 changeNote 字段）
     */
    private void saveSnapshot(BlogPost post, String changeNote) {
        // 获取当前最大版本号
        BlogContentVersion latest = contentVersionMapper.selectOne(
                new LambdaQueryWrapper<BlogContentVersion>()
                        .eq(BlogContentVersion::getPostId, post.getId())
                        .orderByDesc(BlogContentVersion::getVersionNo)
                        .last("LIMIT 1"));
        int nextVersion = (latest == null ? 0 : latest.getVersionNo()) + 1;

        BlogContentVersion version = new BlogContentVersion();
        version.setPostId(post.getId());
        version.setVersionNo(nextVersion);
        version.setTitle(post.getTitle());
        version.setSummary(post.getSummary());
        version.setContentFileId(post.getContentFileId());
        version.setCoverFileId(post.getCoverFileId());
        version.setStatus(post.getStatus());
        version.setVisibility(post.getVisibility());
        version.setChangeType(CHANGE_TYPE_MANUAL);
        version.setChangeNote(changeNote);
        version.setCreatorId(UserContext.getUserId());
        contentVersionMapper.insert(version);

        // 新快照保存后再裁剪，传入当前 contentFileId 避免误删活跃文件
        trimSnapshots(post.getId(), post.getContentFileId());
    }

    /**
     * FIFO 裁剪超限快照。
     * <p>
     * 若快照数超过 {@code maxSnapshotCount}，删除最旧的若干条；
     * 当 {@code snapshotOssCleanup=true} 时，同步删除快照对应的 OSS 文件和 BlogFileAsset 记录，
     * 但会跳过当前文章仍在引用的 {@code currentContentFileId}（防止误删活跃文件）。
     *
     * @param postId             文章 ID
     * @param currentContentFileId 文章当前（修改前）正在引用的内容文件 ID
     */
    private void trimSnapshots(Long postId, Long currentContentFileId) {
        long count = contentVersionMapper.selectCount(
                new LambdaQueryWrapper<BlogContentVersion>()
                        .eq(BlogContentVersion::getPostId, postId));
        if (count <= maxSnapshotCount) {
            return;
        }

        int toDelete = (int) (count - maxSnapshotCount);
        List<BlogContentVersion> oldest = contentVersionMapper.selectList(
                new LambdaQueryWrapper<BlogContentVersion>()
                        .eq(BlogContentVersion::getPostId, postId)
                        .orderByAsc(BlogContentVersion::getVersionNo)
                        .last("LIMIT " + toDelete));

        for (BlogContentVersion old : oldest) {
            if (snapshotOssCleanup
                    && old.getContentFileId() != null
                    && !old.getContentFileId().equals(currentContentFileId)) {
                deleteOssFileAndAsset(old.getContentFileId());
            }
            contentVersionMapper.deleteById(old.getId());
        }
    }

    /**
     * 删除指定文件资产对应的 OSS 文件及数据库记录（仅当 storageType=oss 时执行 OSS 删除）。
     * OSS 删除失败只记录警告，不中断主流程。
     */
    private void deleteOssFileAndAsset(Long fileId) {
        BlogFileAsset asset = fileAssetMapper.selectById(fileId);
        if (asset == null) {
            return;
        }
        if (OSS_STORAGE_TYPE.equals(asset.getStorageType())
                && StringUtils.hasText(asset.getBucket())
                && StringUtils.hasText(asset.getObjectKey())) {
            try {
                ossService.delete(asset.getBucket(), asset.getObjectKey());
            } catch (Exception e) {
                log.warn("Failed to delete OSS file, bucket={}, key={}", asset.getBucket(), asset.getObjectKey(), e);
            }
        }
        fileAssetMapper.deleteById(fileId);
    }

    // ================================================================== 搜索索引事件

    /**
     * 根据文章状态决定发布 upsert 还是 delete 事件：
     * <ul>
     *   <li>published → upsert：组装 PostSearchDocument 后发布事件</li>
     *   <li>draft / archived / 其他 → delete：通知 Meilisearch 移除文档</li>
     * </ul>
     */
    private void publishIndexEvent(BlogPost post) {
        if (STATUS_PUBLISHED.equals(post.getStatus())) {
            PostSearchDocument doc = buildSearchDocument(post);
            eventPublisher.publishEvent(new PostIndexEvent(this, post.getId(), "upsert", doc));
        } else {
            eventPublisher.publishEvent(new PostIndexEvent(this, post.getId(), "delete", null));
        }
    }

    /**
     * 组装 Meilisearch 文档，复用已有的 fetchCategories / fetchTags 方法。
     */
    private PostSearchDocument buildSearchDocument(BlogPost post) {
        List<CategorySummaryVO> cats = fetchCategories(post.getId());
        List<TagSummaryVO> tags = fetchTags(post.getId());

        PostSearchDocument doc = new PostSearchDocument();
        doc.setId(post.getId());
        doc.setTitle(post.getTitle());
        doc.setSummary(post.getSummary());
        doc.setSlug(post.getSlug());
        doc.setStatus(post.getStatus());
        doc.setVisibility(post.getVisibility());
        doc.setAuthorId(post.getAuthorId());
        doc.setCategoryIds(cats.stream().map(CategorySummaryVO::getId).toList());
        doc.setCategoryNames(cats.stream().map(CategorySummaryVO::getName).toList());
        doc.setTagIds(tags.stream().map(TagSummaryVO::getId).toList());
        doc.setTagNames(tags.stream().map(TagSummaryVO::getName).toList());
        // Gson 无法序列化 LocalDateTime（Java 9+ 模块限制），转为 epoch 秒（Long）
        doc.setPublishedAt(toEpochSecond(post.getPublishedAt()));
        doc.setCreateTime(toEpochSecond(post.getCreateTime()));
        doc.setUpdateTime(toEpochSecond(post.getUpdateTime()));
        return doc;
    }

    // ================================================================== OSS / 文件

    /**
     * 新建时优先保存 Markdown 正文到 OSS；兼容旧的 contentFileId 提交方式。
     */
    private Long resolveContentFileId(String content, Long contentFileId, String slug) {
        if (StringUtils.hasText(content)) {
            return saveMarkdownContent(content, slug);
        }
        validateFileExists(contentFileId, "contentFileId");
        return contentFileId;
    }

    /**
     * 将 Markdown 正文上传到 MinIO，并将文件元信息写入 blog_file_asset。
     * <p>
     * 注意：OSS 上传在 @Transactional 内执行。若上传成功但后续 DB 操作失败，
     * 事务回滚后 OSS 文件会成为孤岛文件（低概率，可通过定期清理任务处理）。
     * 相反，若上传失败，异常会中断事务，不会产生无效的数据库记录。
     *
     * @param content Markdown 内容
     * @param slug    文章 slug，用于生成有意义的文件名
     * @return 新创建的 BlogFileAsset ID
     */
    private Long saveMarkdownContent(String content, String slug) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String safeSlug = StringUtils.hasText(slug) ? slug : "article";
        String filename = safeSlug + MARKDOWN_EXTENSION;

        // 生成唯一 objectKey，格式：posts/{yyyy/MM/dd}/{uuid}.md
        String objectKey = ossService.generateObjectKey(filename, "posts");

        String url;
        try {
            url = ossService.upload(defaultBucket, new ByteArrayInputStream(bytes),
                    objectKey, MARKDOWN_MIME_TYPE, (long) bytes.length);
        } catch (Exception e) {
            log.error("Failed to upload markdown to OSS, slug={}, objectKey={}", safeSlug, objectKey, e);
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "文章内容上传失败，请重试");
        }

        BlogFileAsset asset = new BlogFileAsset();
        asset.setStorageType(OSS_STORAGE_TYPE);
        asset.setBucket(defaultBucket);
        asset.setObjectKey(objectKey);
        asset.setUrl(url);
        asset.setFilename(filename);
        asset.setExtension(MARKDOWN_EXTENSION);
        asset.setMimeType(MARKDOWN_MIME_TYPE);
        asset.setSizeBytes((long) bytes.length);
        asset.setHashSha256(sha256(bytes));
        asset.setFileType(MARKDOWN_FILE_TYPE);
        fileAssetMapper.insert(asset);
        return asset.getId();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    // ================================================================== 校验 & 工具

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
        if (!StringUtils.hasText(req.getContent()) && req.getContentFileId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "正文内容不能为空");
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

    private Long resolveCurrentUserId() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "作者不能为空");
        }
        return currentUserId;
    }

    private LocalDateTime resolvePublishedAt(String status, LocalDateTime requested, LocalDateTime current) {
        if (requested != null) {
            return requested;
        }
        if (STATUS_PUBLISHED.equals(status) && current == null) {
            return LocalDateTime.now();
        }
        return current;
    }

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

    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

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

    private void validateFileExists(Long fileId, String fieldName) {
        if (fileId != null && fileAssetMapper.selectById(fileId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 不存在");
        }
    }

    // ================================================================== 分类 & 标签

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

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private void validateCategoriesExist(List<Long> ids) {
        if (!ids.isEmpty() && categoryMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "分类不存在");
        }
    }

    private void validateTagsExist(List<Long> ids) {
        if (!ids.isEmpty() && tagMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签不存在");
        }
    }

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

    // ================================================================== 关联查询

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

    private Set<Long> fetchPostIdsByCategory(Long categoryId) {
        return postCategoryMapper.selectList(
                        new LambdaQueryWrapper<BlogPostCategory>().eq(BlogPostCategory::getCategoryId, categoryId)
                ).stream()
                .map(BlogPostCategory::getPostId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> fetchPostIdsByTag(Long tagId) {
        return postTagMapper.selectList(
                        new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getTagId, tagId)
                ).stream()
                .map(BlogPostTag::getPostId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Long> fetchTagIds(Long postId) {
        return postTagMapper.selectList(
                        new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId)
                ).stream()
                .map(BlogPostTag::getTagId)
                .toList();
    }

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

    // ================================================================== VO 转换

    /**
     * 详情 VO：在 toAdminVO 基础上额外从 OSS 读取 Markdown 正文内容。
     * <p>
     * 仅在 detail() 接口调用，列表接口仍使用 toAdminVO() 避免每行都发起 OSS 读取。
     */
    private PostAdminVO toAdminDetailVO(BlogPost post) {
        PostAdminVO vo = toAdminVO(post);
        if (post.getContentFileId() == null) {
            return vo;
        }
        BlogFileAsset asset = findFileAsset(post.getContentFileId());
        if (asset == null || !OSS_STORAGE_TYPE.equals(asset.getStorageType())
                || !StringUtils.hasText(asset.getBucket())
                || !StringUtils.hasText(asset.getObjectKey())) {
            return vo;
        }
        try {
            InputStream is = ossService.getInputStream(asset.getBucket(), asset.getObjectKey());
            vo.setContent(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to read content from OSS, fileId={}, objectKey={}",
                    post.getContentFileId(), asset.getObjectKey(), e);
        }
        return vo;
    }

    private CategorySummaryVO toCategorySummaryVO(BlogCategory category) {
        CategorySummaryVO vo = new CategorySummaryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        return vo;
    }

    private TagSummaryVO toTagSummaryVO(BlogTag tag) {
        TagSummaryVO vo = new TagSummaryVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        return vo;
    }

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
            vo.setContentUrl(resolveFileUrl(content));
        }
        BlogFileAsset cover = findFileAsset(post.getCoverFileId());
        if (cover != null) {
            vo.setCoverUrl(resolveFileUrl(cover));
        }

        vo.setCategories(fetchCategories(post.getId()));
        vo.setTags(fetchTags(post.getId()));
        return vo;
    }

    private BlogFileAsset findFileAsset(Long fileId) {
        return fileId == null ? null : fileAssetMapper.selectById(fileId);
    }

    /**
     * 将 LocalDateTime 转为 UTC epoch 秒（供 Gson 序列化到 Meilisearch 使用）。
     * Gson 不支持 java.time 类型的反射序列化（Java 9+ 模块访问限制），
     * 使用 Long 可同时满足 Meilisearch 可排序数值要求。
     */
    private static Long toEpochSecond(LocalDateTime dt) {
        return dt == null ? null : dt.toEpochSecond(ZoneOffset.UTC);
    }

    /**
     * 将文件资产解析为可访问 URL。
     * <p>
     * 对于 OSS（私有桶）类型的文件，直接访问会被拒绝（HTTP 403），
     * 因此通过 {@link ObjectStorageService#getPresignedUrl} 生成带时效的预签名 URL。
     * 非 OSS 文件（本地存储）直接返回存储的 url 字段。
     *
     * @param asset 文件资产，为 null 时返回 null
     * @return 可访问的 URL（预签名 or 直接 URL），解析失败时降级返回原 url 字段
     */
    private String resolveFileUrl(BlogFileAsset asset) {
        if (asset == null) {
            return null;
        }
        if (OSS_STORAGE_TYPE.equals(asset.getStorageType())
                && StringUtils.hasText(asset.getBucket())
                && StringUtils.hasText(asset.getObjectKey())) {
            try {
                return ossService.getPresignedUrl(asset.getBucket(), asset.getObjectKey(),
                        presignedUrlExpirySeconds);
            } catch (Exception e) {
                log.warn("Failed to generate presigned URL, bucket={}, key={}",
                        asset.getBucket(), asset.getObjectKey(), e);
            }
        }
        return asset.getUrl();
    }
}
