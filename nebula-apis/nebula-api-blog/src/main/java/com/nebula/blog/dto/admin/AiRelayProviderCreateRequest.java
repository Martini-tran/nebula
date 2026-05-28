package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商创建请求
 */
@Data
public class AiRelayProviderCreateRequest {

    @NotBlank(message = "服务商名称不能为空")
    @Size(max = 100, message = "服务商名称长度不能超过100")
    private String name;

    @Size(max = 255, message = "官网地址长度不能超过255")
    private String websiteUrl;

    private Long logoFileId;

    @Size(max = 1000, message = "服务商简介长度不能超过1000")
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
}
