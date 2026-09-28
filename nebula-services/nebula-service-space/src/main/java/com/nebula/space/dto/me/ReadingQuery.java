package com.nebula.space.dto.me;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 稍后读列表查询
 */
@Data
public class ReadingQuery {

    @Pattern(regexp = "unread|reading|done", message = "不支持的阅读状态")
    private String status;

    /**
     * true 只看归档的；不传或 false 只看队列里的
     */
    private Boolean archived;
}
