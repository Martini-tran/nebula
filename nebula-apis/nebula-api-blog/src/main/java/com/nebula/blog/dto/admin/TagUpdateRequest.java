package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 后台标签更新请求
 */
@Data
public class TagUpdateRequest {

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签别名
     */
    private String slug;
}
