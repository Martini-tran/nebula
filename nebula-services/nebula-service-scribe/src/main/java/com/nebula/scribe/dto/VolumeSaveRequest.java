package com.nebula.scribe.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/修改卷请求
 *
 * <p>新建时标题为空按「第 N 卷」命名；修改为整表单覆盖，标题不能为空。</p>
 */
@Data
public class VolumeSaveRequest {

    /**
     * 卷名
     */
    @Size(max = 100, message = "卷名长度不能超过100")
    private String title;

    /**
     * 本卷梗概
     */
    @Size(max = 1000, message = "卷梗概长度不能超过1000")
    private String synopsis;
}
