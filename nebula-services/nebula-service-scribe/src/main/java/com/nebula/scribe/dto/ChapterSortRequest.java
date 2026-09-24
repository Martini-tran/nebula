package com.nebula.scribe.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 章节排序请求：按新顺序给出本作品的全部章节 ID
 */
@Data
public class ChapterSortRequest {

    @NotEmpty(message = "章节顺序不能为空")
    private List<Long> ids;
}
