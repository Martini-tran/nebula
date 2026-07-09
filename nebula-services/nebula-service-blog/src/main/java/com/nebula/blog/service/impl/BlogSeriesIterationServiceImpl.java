package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.SeriesCatalogCreateRequest;
import com.nebula.blog.dto.admin.SeriesCreateRequest;
import com.nebula.blog.dto.admin.SeriesIterationAppendRequest;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogSeries;
import com.nebula.blog.entity.BlogSeriesCatalog;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogPostMapper;
import com.nebula.blog.mapper.BlogSeriesMapper;
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.service.BlogSeriesAdminService;
import com.nebula.blog.service.BlogSeriesCatalogAdminService;
import com.nebula.blog.service.BlogSeriesIterationService;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 迭代链回调落库服务实现（编排 webhook 消费端）
 * 复用现有 admin service 完整落库（含正文转文件、slug 校验、索引事件）——只解决"回调无登录态"这一点：
 * 调用前把系统用户塞进 {@link UserContext}，调用后 clear。见 docs/编排回调Webhook设计.md。
 *
 * <p><b>幂等</b>：文章 slug = {@code seriesSlug-seq}，撞 uk_slug 即本轮已落过，返回既有 postId，不重复建。
 * 系列按 {@code chain_id} 查/建（首轮建、记 chain_id，后续复用）；目录用系列下第一个"正文"节点，缺则建。
 *
 * @author nebula
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSeriesIterationServiceImpl implements BlogSeriesIterationService {

    private final BlogSeriesMapper seriesMapper;

    private final BlogSeriesCatalogMapper catalogMapper;

    private final BlogSeriesCatalogPostMapper catalogPostMapper;

    private final BlogPostMapper postMapper;

    private final BlogSeriesAdminService seriesAdminService;

    private final BlogPostAdminService postAdminService;

    private final BlogSeriesCatalogAdminService catalogAdminService;

    /**
     * 回调落库归属的系统用户 id（回调无登录态，用它兜底 create_by/author_id）
     */
    @Value("${nebula.blog.iteration.author-id:1}")
    private Long authorId;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long append(SeriesIterationAppendRequest req) {
        validate(req);
        // 回调无登录态：临时把系统用户塞进 UserContext，复用 admin service 的 resolveCurrentUserId，finally 清除
        UserContext.set(authorId);
        try {
            String seriesSlug = StringUtils.hasText(req.seriesSlug())
                    ? req.seriesSlug() : slugifyChain(req.getChainId());
            String postSlug = seriesSlug + "-" + req.getSeq();

            // 幂等：本轮文章已落过（slug 撞）→ 返回既有 id，不重复建
            BlogPost exist = postMapper.selectOne(new LambdaQueryWrapper<BlogPost>()
                    .eq(BlogPost::getSlug, postSlug).last("limit 1"));
            if (exist != null) {
                log.info("迭代链[{}]第 {} 篇已落库(post={})，幂等返回", req.getChainId(), req.getSeq(), exist.getId());
                return exist.getId();
            }

            Long seriesId = resolveOrCreateSeries(req, seriesSlug);
            Long catalogId = resolveOrCreateCatalog(seriesId);

            // 建文章（草稿、AI 来源），正文转文件由 admin service 内部处理
            PostCreateRequest post = new PostCreateRequest();
            post.setTitle(req.articleTitle());
            post.setSlug(postSlug);
            post.setSummary(req.articleSummary());
            post.setContent(req.articleBody());
            post.setStatus("draft");
            post.setVisibility("public");
//            post.setSourceType("AI");
            Long postId = postAdminService.create(post);

            // 追加到系列目录节点（不用 bindPosts——那是全量替换，会覆盖前面的文章）
            BlogSeriesCatalogPost rel = new BlogSeriesCatalogPost();
            rel.setCatalogId(catalogId);
            rel.setPostId(postId);
            rel.setSortOrder(req.getSeq());
            rel.setIsPrimary(Boolean.FALSE);
            catalogPostMapper.insert(rel);

            log.info("迭代链[{}]第 {} 篇落库成功: series={}, catalog={}, post={}",
                    req.getChainId(), req.getSeq(), seriesId, catalogId, postId);
            return postId;
        } finally {
            UserContext.clear();
        }
    }

    /* ===================== 内部 ===================== */

    /**
     * 按 chain_id 查系列，无则建（首轮），并记 chain_id 映射。
     */
    private Long resolveOrCreateSeries(SeriesIterationAppendRequest req, String seriesSlug) {
        BlogSeries series = seriesMapper.selectOne(new LambdaQueryWrapper<BlogSeries>()
                .eq(BlogSeries::getChainId, req.getChainId()).last("limit 1"));
        if (series != null) {
            return series.getId();
        }
        SeriesCreateRequest sr = new SeriesCreateRequest();
        sr.setName(StringUtils.hasText(req.seriesName()) ? req.seriesName() : req.getChainId());
        sr.setSlug(seriesSlug);
        sr.setStatus("draft");
        sr.setVisibility("public");
        Long seriesId = seriesAdminService.create(sr);
        // 回写 chain_id 映射（admin service 不认识 chainId，单独更新）
        BlogSeries patch = new BlogSeries();
        patch.setId(seriesId);
        patch.setChainId(req.getChainId());
        seriesMapper.updateById(patch);
        return seriesId;
    }

    /**
     * 取系列下第一个目录节点，无则建一个"正文"节点。
     */
    private Long resolveOrCreateCatalog(Long seriesId) {
        BlogSeriesCatalog node = catalogMapper.selectOne(new LambdaQueryWrapper<BlogSeriesCatalog>()
                .eq(BlogSeriesCatalog::getSeriesId, seriesId)
                .orderByAsc(BlogSeriesCatalog::getId).last("limit 1"));
        if (node != null) {
            return node.getId();
        }
        SeriesCatalogCreateRequest cr = new SeriesCatalogCreateRequest();
        cr.setSeriesId(seriesId);
        cr.setTitle("正文");
        cr.setNodeType(0);
        cr.setSortOrder(0);
        return catalogAdminService.create(cr);
    }

    private String slugifyChain(String chainId) {
        return "series-" + chainId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private void validate(SeriesIterationAppendRequest req) {
        if (req == null || !StringUtils.hasText(req.getChainId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "chainId 不能为空");
        }
        if (req.getSeq() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "seq 不能为空");
        }
        if (!StringUtils.hasText(req.articleTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "articleTitle（context.articleTitle）不能为空");
        }
    }
}
