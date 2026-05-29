package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商管理VO
 */
@Data
public class AiRelayProviderAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String websiteUrl;

    private Long logoFileId;

    private String logoUrl;

    private String description;

    private BigDecimal recommendScore;

    private Integer sortOrder;

    private Integer status;

    /**
     * 最近一次同步时间（运营手动同步时刷新，可为空）
     */
    private LocalDateTime lastSyncTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
