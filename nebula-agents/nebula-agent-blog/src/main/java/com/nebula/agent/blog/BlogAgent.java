package com.nebula.agent.blog;

import com.nebula.agent.blog.domain.BlogSeries;
import com.nebula.agent.blog.domain.BlogSeriesOutline;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.common.ai.agent.Agent;

/**
 * 博客Agent
 * 负责博客领域的AI能力。博客创作分多步推进：先「生成系列主题」规划出整个系列的选题大纲，
 * 再据此逐篇生成正文。当前已落地第一步。
 *
 * @author nebula
 */
public interface BlogAgent extends Agent {

    /**
     * 生成系列主题
     * 围绕给定主题规划出整个系列的选题大纲：系列标题、系列简介，以及若干篇选题
     * （每篇含序号、标题、简介、分类与标签），但不生成正文。
     * 这是博客创作的第一步，其产物作为后续逐篇正文生成的输入。
     *
     * @param request 系列生成请求
     * @return 系列选题大纲
     */
    BlogSeriesOutline generateSeriesTopics(BlogSeriesRequest request);

    /**
     * 根据主题生成系列文章
     * 每篇文章包含正文、分类与标签。
     *
     * @param request 系列文章生成请求
     * @return 生成的系列文章
     */
    BlogSeries generateSeries(BlogSeriesRequest request);
}
