package com.nebula.agent.blog.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 系列文章
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class BlogSeries {

    /**
     * 系列标题
     */
    private String title;

    /**
     * 系列下的文章列表
     */
    private List<BlogArticle> articles = new ArrayList<>();
}
