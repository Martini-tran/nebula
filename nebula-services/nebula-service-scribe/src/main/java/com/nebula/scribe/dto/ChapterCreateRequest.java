package com.nebula.scribe.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建章节请求
 */
@Data
public class ChapterCreateRequest {

    /**
     * 章节标题；为空时按「第 N 章」自动命名
     */
    @Size(max = 100, message = "章节标题长度不能超过100")
    private String title;
}
