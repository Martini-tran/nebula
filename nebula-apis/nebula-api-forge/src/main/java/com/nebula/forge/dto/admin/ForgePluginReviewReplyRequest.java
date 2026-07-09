package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件评价回复请求
 *
 * @author nebula
 */
@Data
public class ForgePluginReviewReplyRequest {

    /**
     * 回复内容
     */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容长度不能超过2000")
    private String replyContent;
}
