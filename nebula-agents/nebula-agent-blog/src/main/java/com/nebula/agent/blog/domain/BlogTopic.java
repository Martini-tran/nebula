package com.nebula.agent.blog.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 系列选题
 * 描述系列中的一篇待创作文章，只含主题规划信息（标题、简介、分类、标签），不含正文。
 * 正文由后续的逐篇生成能力补全。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class BlogTopic {

    /**
     * 在系列中的序号，从1开始
     */
    private int order;

    /**
     * 选题标题
     */
    private String title;

    /**
     * 选题简介，概述该篇将写什么
     */
    private String summary;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags = new ArrayList<>();
}
