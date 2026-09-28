package com.nebula.space.dto.me;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分享页下载：带上提取码
 */
@Data
public class ShareDownloadRequest {

    @Size(max = 16, message = "提取码不对")
    private String password;
}
