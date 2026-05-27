package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 游记关联文章简要VO（前台）
 */
@Data
public class TravelTripPostSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long postId;

    private String slug;

    private String title;

    private String summary;

    private String coverUrl;

    /**
     * 关联类型：0主要文章 1相关推荐
     */
    private Integer postType;
}
