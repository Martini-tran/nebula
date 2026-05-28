package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI中转服务商优势 管理VO
 */
@Data
public class AiRelayProviderAdvantageAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    private String title;

    private String content;

    private Integer advantageType;

    private Long iconFileId;

    private String iconUrl;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
