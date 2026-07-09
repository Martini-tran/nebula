package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件版本审核请求
 *
 * @author nebula
 */
@Data
public class ForgePluginVersionReviewRequest {

    /**
     * 审核结果：1通过 2拒绝
     */
    @NotNull(message = "审核结果不能为空")
    private Integer reviewStatus;

    /**
     * 审核备注
     */
    @Size(max = 1000, message = "审核备注长度不能超过1000")
    private String reviewRemark;
}
