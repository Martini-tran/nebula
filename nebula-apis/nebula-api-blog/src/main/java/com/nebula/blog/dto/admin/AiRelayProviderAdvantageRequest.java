package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI中转服务商优势 创建/更新请求
 */
@Data
public class AiRelayProviderAdvantageRequest {

    @NotBlank(message = "优势标题不能为空")
    @Size(max = 100, message = "优势标题长度不能超过100")
    private String title;

    @Size(max = 500, message = "优势说明长度不能超过500")
    private String content;

    /**
     * 优势类型（1普通优势 2核心优势 3风险提示）
     */
    private Integer advantageType = 1;

    private Long iconFileId;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
