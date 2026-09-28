package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 生成分享链接
 */
@Data
public class ShareCreateRequest {

    @NotNull(message = "要分享哪个文件")
    private Long fileId;

    /**
     * 有效天数，到那天 23:59:59 过期；空为永久
     */
    @Min(value = 1, message = "有效期至少 1 天")
    @Max(value = 3650, message = "有效期最多 3650 天")
    private Integer days;

    private boolean withPassword;

    /**
     * 最多下载次数，空为不限
     */
    @Min(value = 1, message = "下载次数至少 1 次")
    @Max(value = 1000, message = "下载次数最多 1000 次")
    private Integer maxDownloads;
}
