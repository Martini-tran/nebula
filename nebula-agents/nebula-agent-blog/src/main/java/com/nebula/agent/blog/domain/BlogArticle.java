package com.nebula.agent.blog.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 单篇博客文章
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class BlogArticle {

    /**
     * 标题
     */
    private String title;

    /**
     * 正文
     */
    private String content;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags = new ArrayList<>();
}
