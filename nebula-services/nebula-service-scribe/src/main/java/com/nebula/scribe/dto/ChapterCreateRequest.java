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

    /**
     * 放进哪一卷；有卷的作品不传则放进最后一卷，无卷的作品必须不传
     */
    private Long volumeId;
}
