package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 系列主题生成请求DTO
 * 由AI博客Agent围绕主题规划出整个系列的选题大纲，并落库为系列与目录节点。
 */
@Data
public class SeriesGenerateRequest {

    /**
     * 主题
     */
    private String topic;

    /**
     * 期望生成的选题篇数，默认 5
     */
    private Integer articleCount = 5;

    /**
     * 目标读者
     */
    private String audience;

    /**
     * 文风
     */
    private String style;

    /**
     * 语言，默认中文
     */
    private String language = "中文";
}
