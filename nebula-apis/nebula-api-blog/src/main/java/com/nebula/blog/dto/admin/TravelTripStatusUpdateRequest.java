package com.nebula.blog.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游记状态变更请求
 */
@Data
public class TravelTripStatusUpdateRequest {

    /**
     * 状态：draft/published/archived
     */
    private String status;

    /**
     * 可见性：public/private
     */
    private String visibility;

    /**
     * 发布时间（可选；状态切到 published 且未传时由服务端使用当前时间）
     */
    private LocalDateTime publishedAt;
}
