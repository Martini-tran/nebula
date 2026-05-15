package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 标签VO（前端）
 */
@Data
public class TagVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签别名（URL友好）
     */
    private String slug;

    /**
     * 关联文章数量
     */
    private Integer postCount;
}
