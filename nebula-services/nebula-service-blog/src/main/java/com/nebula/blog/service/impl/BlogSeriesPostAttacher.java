package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.SeriesCatalogCreateRequest;
import com.nebula.blog.entity.BlogSeriesCatalog;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import com.nebula.blog.mapper.BlogSeriesCatalogMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogPostMapper;
import com.nebula.blog.service.BlogSeriesCatalogAdminService;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 把文章<b>追加</b>到系列目录
 * 发布接口与迭代链回调共用。不走 {@code bindPosts}——那是整节点全量替换，会把节点下已有的文章冲掉。
 *
 * <p>文章只挂「文章集合」类型（node_type=1）的节点：前台目录树只给这类节点展示文章，
 * 挂到目录类型（node_type=0）的节点上，文章在目录树里是看不到的。</p>
 */
@Component
@RequiredArgsConstructor
public class BlogSeriesPostAttacher {

    private static final int NODE_TYPE_POST_GROUP = 1;
    private static final String DEFAULT_CATALOG_TITLE = "正文";

    private final BlogSeriesCatalogMapper catalogMapper;
    private final BlogSeriesCatalogPostMapper catalogPostMapper;
    private final BlogSeriesCatalogAdminService catalogAdminService;

    /**
     * 定位要挂载的「文章集合」节点：指定 ID → 按标题找（无则建）→ 系列下第一个（无则建「正文」）。
     *
     * @param seriesId     系列ID
     * @param catalogId    目录节点ID，可空；须属于该系列且为「文章集合」类型
     * @param catalogTitle 目录节点标题，可空
     * @return 目录节点ID
     */
    public Long resolveCatalog(Long seriesId, Long catalogId, String catalogTitle) {
        if (catalogId != null) {
            BlogSeriesCatalog node = catalogMapper.selectById(catalogId);
            if (node == null || !seriesId.equals(node.getSeriesId())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "目录节点不存在或不属于该系列");
            }
            if (node.getNodeType() == null || node.getNodeType() != NODE_TYPE_POST_GROUP) {
                throw new BizException(HttpStatus.BAD_REQUEST, "目录节点不是「文章集合」类型，不能挂载文章");
            }
            return node.getId();
        }

        String title = StringUtils.hasText(catalogTitle) ? catalogTitle.trim() : null;
        BlogSeriesCatalog node = catalogMapper.selectOne(new LambdaQueryWrapper<BlogSeriesCatalog>()
                .eq(BlogSeriesCatalog::getSeriesId, seriesId)
                .eq(BlogSeriesCatalog::getNodeType, NODE_TYPE_POST_GROUP)
                .eq(title != null, BlogSeriesCatalog::getTitle, title)
                .orderByAsc(BlogSeriesCatalog::getSortOrder)
                .orderByAsc(BlogSeriesCatalog::getId)
                .last("limit 1"));
        if (node != null) {
            return node.getId();
        }

        SeriesCatalogCreateRequest create = new SeriesCatalogCreateRequest();
        create.setSeriesId(seriesId);
        create.setTitle(title != null ? title : DEFAULT_CATALOG_TITLE);
        create.setNodeType(NODE_TYPE_POST_GROUP);
        create.setSortOrder(nextCatalogSortOrder(seriesId));
        return catalogAdminService.create(create);
    }

    /**
     * 追加文章到目录节点。已挂载过则幂等，仅在显式传了排序时调整位置。
     *
     * @param sortOrder 节点内排序，为空则排到末尾
     */
    public void attach(Long catalogId, Long postId, Integer sortOrder) {
        BlogSeriesCatalogPost rel = catalogPostMapper.selectOne(new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                .eq(BlogSeriesCatalogPost::getCatalogId, catalogId)
                .eq(BlogSeriesCatalogPost::getPostId, postId)
                .last("limit 1"));
        if (rel == null) {
            rel = new BlogSeriesCatalogPost();
            rel.setCatalogId(catalogId);
            rel.setPostId(postId);
            rel.setSortOrder(sortOrder != null ? sortOrder : nextPostSortOrder(catalogId));
            rel.setIsPrimary(Boolean.FALSE);
            catalogPostMapper.insert(rel);
        } else if (sortOrder != null && !sortOrder.equals(rel.getSortOrder())) {
            rel.setSortOrder(sortOrder);
            catalogPostMapper.updateById(rel);
        }
    }

    private int nextCatalogSortOrder(Long seriesId) {
        BlogSeriesCatalog last = catalogMapper.selectOne(new LambdaQueryWrapper<BlogSeriesCatalog>()
                .eq(BlogSeriesCatalog::getSeriesId, seriesId)
                .orderByDesc(BlogSeriesCatalog::getSortOrder)
                .last("limit 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private int nextPostSortOrder(Long catalogId) {
        BlogSeriesCatalogPost last = catalogPostMapper.selectOne(new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                .eq(BlogSeriesCatalogPost::getCatalogId, catalogId)
                .orderByDesc(BlogSeriesCatalogPost::getSortOrder)
                .last("limit 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }
}
