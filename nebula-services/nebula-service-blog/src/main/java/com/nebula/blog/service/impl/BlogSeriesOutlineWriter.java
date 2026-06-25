package com.nebula.blog.service.impl;

import com.nebula.agent.blog.domain.BlogSeriesOutline;
import com.nebula.agent.blog.domain.BlogTopic;
import com.nebula.blog.dto.admin.SeriesCatalogCreateRequest;
import com.nebula.blog.dto.admin.SeriesCreateRequest;
import com.nebula.blog.service.BlogSeriesAdminService;
import com.nebula.blog.service.BlogSeriesCatalogAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 系列选题大纲落库器
 * 把AI生成的 {@link BlogSeriesOutline} 写入 blog_series（一行系列，草稿态）与 blog_series_catalog
 * （每个选题一个「文章集合」节点）。整个写入在单一事务内完成，并复用既有管理服务以沿用其
 * slug唯一性校验、目录 path/level 计算、childrenCount 维护等约束。
 *
 * <p>AI调用（可能较慢）刻意放在本写入事务之外，由编排层先取得大纲再调用本类，避免长事务。
 *
 * @author nebula
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogSeriesOutlineWriter {

    /**
     * 选题节点类型：文章集合（与 BlogSeriesCatalogAdminServiceImpl 的 NODE_TYPE_POST_GROUP 对齐）
     */
    private static final int NODE_TYPE_POST_GROUP = 1;

    /**
     * slug 最大长度，与系列管理服务保持一致
     */
    private static final int SLUG_MAX_LENGTH = 120;

    private final BlogSeriesAdminService seriesAdminService;
    private final BlogSeriesCatalogAdminService catalogAdminService;

    /**
     * 落库系列选题大纲
     *
     * @param outline       AI生成的系列选题大纲
     * @param fallbackTitle 大纲未给出系列标题时回退使用的标题（一般为请求主题）
     * @return 新建系列ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long write(BlogSeriesOutline outline, String fallbackTitle) {
        String seriesName = StringUtils.hasText(outline.getTitle()) ? outline.getTitle() : fallbackTitle;

        SeriesCreateRequest seriesReq = new SeriesCreateRequest();
        seriesReq.setName(seriesName);
        seriesReq.setSlug(generateSlug(seriesName));
        seriesReq.setDescription(outline.getSummary());
        // status/visibility/isFinished/sortOrder 沿用 DTO 默认值（draft/public/false/0）
        Long seriesId = seriesAdminService.create(seriesReq);

        List<BlogTopic> topics = outline.getTopics();
        if (topics != null) {
            int index = 1;
            for (BlogTopic topic : topics) {
                if (topic == null || !StringUtils.hasText(topic.getTitle())) {
                    continue;
                }
                SeriesCatalogCreateRequest catalogReq = new SeriesCatalogCreateRequest();
                catalogReq.setSeriesId(seriesId);
                catalogReq.setParentId(null);
                catalogReq.setTitle(topic.getTitle());
                catalogReq.setNodeType(NODE_TYPE_POST_GROUP);
                catalogReq.setSortOrder(topic.getOrder() > 0 ? topic.getOrder() : index);
                catalogAdminService.create(catalogReq);
                index++;
            }
        }
        log.info("系列选题大纲落库完成，seriesId={}，选题数={}", seriesId, topics == null ? 0 : topics.size());
        return seriesId;
    }

    /**
     * 生成系列 slug
     * 沿用站内 slugify 规则（小写、空白转-、移除非[a-z0-9-]）；中文标题会被清空，
     * 此时回退为 series-&lt;短uuid&gt;，确保非空且大概率唯一（唯一性最终由系列创建服务再校验）。
     *
     * @param title 系列标题
     * @return slug
     */
    private String generateSlug(String title) {
        String slug = StringUtils.hasText(title)
                ? title.toLowerCase()
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                : "";
        if (!StringUtils.hasText(slug)) {
            slug = "series-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (slug.length() > SLUG_MAX_LENGTH) {
            slug = slug.substring(0, SLUG_MAX_LENGTH);
        }
        return slug;
    }
}
