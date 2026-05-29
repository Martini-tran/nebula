package com.nebula.blog.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商创建请求
 */
@Data
public class AiRelayProviderCreateRequest {

    /**
     * 服务商名称（必填，最长 100）
     */
    private String name;

    /**
     * 官网地址（最长 255）
     */
    private String websiteUrl;

    private Long logoFileId;

    /**
     * 服务商简介（最长 1000）
     */
    private String description;

    /**
     * 综合推荐分
     */
    private BigDecimal recommendScore;

    private Integer sortOrder = 0;

    /**
     * 状态（1正常 0下线），默认1
     */
    private Integer status = 1;

    /**
     * 最近一次同步时间，可为空
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;
}
