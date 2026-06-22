package com.nebula.forge.dto.front;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表/更新插件评价请求（前台）
 *
 * @author nebula
 */
@Data
public class ForgePluginReviewCreateRequest {

    /**
     * 评分：1-5
     */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer rating;

    /**
     * 评价内容
     */
    @Size(max = 2000, message = "评价内容长度不能超过2000")
    private String content;

    /**
     * 评价时安装的版本ID，可选
     */
    private Long versionId;
}
