package com.nebula.agent.blog;

import com.nebula.agent.blog.domain.BlogSeries;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.common.ai.agent.Agent;

/**
 * 博客Agent
 * 负责博客领域的AI能力，首个能力为系列文章生成。
 *
 * @author nebula
 */
public interface BlogAgent extends Agent {

    /**
     * 根据主题生成系列文章
     * 每篇文章包含正文、分类与标签。
     *
     * @param request 系列文章生成请求
     * @return 生成的系列文章
     */
    BlogSeries generateSeries(BlogSeriesRequest request);
}
