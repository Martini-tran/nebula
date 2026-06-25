package com.nebula.agent.blog.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * 系列文章生成请求
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class BlogSeriesRequest {

    /**
     * 归属用户ID（用于长期记忆按用户隔离的召回与写入）
     */
    private String userId;

    /**
     * 主题
     */
    private String topic;

    /**
     * 期望生成的文章篇数
     */
    private int articleCount;

    /**
     * 目标读者
     */
    private String audience;

    /**
     * 文风
     */
    private String style;

    /**
     * 语言
     */
    private String language;

    /**
     * 额外模板参数
     */
    private Map<String, Object> variables = new HashMap<>();
}
