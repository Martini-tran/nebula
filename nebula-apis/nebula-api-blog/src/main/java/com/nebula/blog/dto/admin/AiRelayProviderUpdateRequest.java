package com.nebula.blog.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商更新请求
 */
@Data
public class AiRelayProviderUpdateRequest {

    /**
     * 服务商名称（最长 100）
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

    private BigDecimal recommendScore;

    private Integer sortOrder;

    private Integer status;

    /**
     * 最近一次同步时间。null 表示不修改；如需清空请由调用方决定语义
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;
}
