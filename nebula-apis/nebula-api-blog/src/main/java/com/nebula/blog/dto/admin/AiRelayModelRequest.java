package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI模型 创建/更新请求
 */
@Data
public class AiRelayModelRequest {

    @NotBlank(message = "模型编码不能为空")
    @Size(max = 100, message = "模型编码长度不能超过100")
    private String code;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100")
    private String name;

    @Size(max = 50, message = "模型厂商长度不能超过50")
    private String modelVendor;

    /**
     * 模型类型（1文本 2图像 3音频 4多模态 5Embedding）
     */
    @NotNull(message = "模型类型不能为空")
    private Integer modelType;

    @Size(max = 1000, message = "模型说明长度不能超过1000")
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
