package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostPublishRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogSeries;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogSeriesMapper;
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.service.BlogPostPublishService;
import com.nebula.blog.vo.admin.PostPublishVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 文章发布服务实现
 *
 * <p>文章的建与改复用 {@link BlogPostAdminService}（正文转文件、slug 校验、版本快照、索引事件都在那里），
 * 挂系列目录交给 {@link BlogSeriesPostAttacher}；本类只负责按 slug 判断走新建还是更新，并把两步串在一个事务里。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogPostPublishServiceImpl implements BlogPostPublishService {

    private static final String STATUS_PUBLISHED = "published";
    private static final String CHANGE_NOTE = "接口发布";

    private final BlogPostMapper postMapper;
    private final BlogSeriesMapper seriesMapper;
    private final BlogPostAdminService postAdminService;
    private final BlogSeriesPostAttacher seriesPostAttacher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostPublishVO publish(PostPublishRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getSlug())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文章 slug 不能为空（发布接口以 slug 作为幂等键）");
        }
        // 先定位系列再动文章：系列写错时直接报错，不留下一篇没挂上系列的文章等人收拾
        PostPublishRequest.SeriesRef seriesRef = req.getSeries();
        BlogSeries series = seriesRef == null ? null : resolveSeries(seriesRef);

        String status = StringUtils.hasText(req.getStatus()) ? req.getStatus() : STATUS_PUBLISHED;
        BlogPost existing = postMapper.selectOne(new LambdaQueryWrapper<BlogPost>()
                .eq(BlogPost::getSlug, req.getSlug()).last("limit 1"));
        boolean created = existing == null;
        Long postId;
        if (created) {
            postId = postAdminService.create(toCreateRequest(req, status));
        } else {
            postId = existing.getId();
            postAdminService.update(postId, toUpdateRequest(req, status));
        }

        PostPublishVO vo = new PostPublishVO();
        vo.setId(postId);
        vo.setSlug(req.getSlug());
        vo.setCreated(created);
        if (series != null) {
            Long catalogId = seriesPostAttacher.resolveCatalog(
                    series.getId(), seriesRef.getCatalogId(), seriesRef.getCatalog());
            seriesPostAttacher.attach(catalogId, postId, seriesRef.getSortOrder());
            vo.setSeriesId(series.getId());
            vo.setCatalogId(catalogId);
        }
        log.info("文章发布完成: post={}, slug={}, created={}, series={}, catalog={}",
                postId, req.getSlug(), created, vo.getSeriesId(), vo.getCatalogId());
        return vo;
    }

    /**
     * 未传的可选字段不覆盖 {@link PostCreateRequest} 自带的默认值。
     */
    private PostCreateRequest toCreateRequest(PostPublishRequest req, String status) {
        PostCreateRequest create = new PostCreateRequest();
        create.setTitle(req.getTitle());
        create.setSlug(req.getSlug());
        create.setSummary(req.getSummary());
        create.setContent(req.getContent());
        create.setCoverFileId(req.getCoverFileId());
        create.setStatus(status);
        create.setPublishedAt(req.getPublishedAt());
        create.setCategoryIds(req.getCategoryIds());
        create.setTagIds(req.getTagIds());
        if (StringUtils.hasText(req.getPostType())) {
            create.setPostType(req.getPostType());
        }
        if (StringUtils.hasText(req.getVisibility())) {
            create.setVisibility(req.getVisibility());
        }
        if (StringUtils.hasText(req.getSourceType())) {
            create.setSourceType(req.getSourceType());
        }
        if (req.getIsOriginal() != null) {
            create.setIsOriginal(req.getIsOriginal());
        }
        return create;
    }

    /**
     * 更新语义沿用后台：字段为空即不改。slug 是定位用的，不参与更新。
     */
    private PostUpdateRequest toUpdateRequest(PostPublishRequest req, String status) {
        PostUpdateRequest update = new PostUpdateRequest();
        update.setTitle(req.getTitle());
        update.setPostType(req.getPostType());
        update.setSummary(req.getSummary());
        update.setContent(req.getContent());
        update.setCoverFileId(req.getCoverFileId());
        update.setStatus(status);
        update.setVisibility(req.getVisibility());
        update.setSourceType(req.getSourceType());
        update.setIsOriginal(req.getIsOriginal());
        update.setPublishedAt(req.getPublishedAt());
        update.setCategoryIds(req.getCategoryIds());
        update.setTagIds(req.getTagIds());
        update.setChangeNote(CHANGE_NOTE);
        return update;
    }

    /**
     * 按 ID 或 slug 定位系列。找不到就报错，不自动建：拼错 slug 时不该悄悄多出一个系列。
     */
    private BlogSeries resolveSeries(PostPublishRequest.SeriesRef ref) {
        BlogSeries series;
        if (ref.getId() != null) {
            series = seriesMapper.selectById(ref.getId());
        } else if (StringUtils.hasText(ref.getSlug())) {
            series = seriesMapper.selectOne(new LambdaQueryWrapper<BlogSeries>()
                    .eq(BlogSeries::getSlug, ref.getSlug()).last("limit 1"));
        } else {
            throw new BizException(HttpStatus.BAD_REQUEST, "series.id 与 series.slug 至少传一个");
        }
        if (series == null) {
            throw new BizException(HttpStatus.NOT_FOUND,
                    "系列不存在: " + (ref.getId() != null ? ref.getId() : ref.getSlug()));
        }
        return series;
    }
}
