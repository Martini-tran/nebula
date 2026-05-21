package com.nebula.blog.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台文章状态变更请求
 */
@Data
public class PostStatusUpdateRequest {

    /**
     * 目标状态
     */
    private String status;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;
}
