package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件评价审核请求
 *
 * @author nebula
 */
@Data
public class ForgePluginReviewAuditRequest {

    /**
     * 状态：0待审 1展示 2隐藏 3拒绝
     */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /**
     * 审核备注
     */
    @Size(max = 1000, message = "审核备注长度不能超过1000")
    private String auditRemark;
}
