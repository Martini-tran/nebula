package com.nebula.agent.blog.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 系列选题大纲
 * 「生成系列主题」能力的产物：围绕一个主题规划出整个系列的标题、简介与各篇选题，
 * 作为后续逐篇正文生成的输入。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class BlogSeriesOutline {

    /**
     * 系列标题
     */
    private String title;

    /**
     * 系列简介，概述整个系列的目标与脉络
     */
    private String summary;

    /**
     * 系列下的选题列表
     */
    private List<BlogTopic> topics = new ArrayList<>();
}
