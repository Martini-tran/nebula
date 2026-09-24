package com.nebula.scribe.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 卷，字段对齐前端 {@code Volume}；章数、字数由前端按章节列表聚合
 */
@Data
public class VolumeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long workId;

    private String title;

    /**
     * 本卷梗概
     */
    private String synopsis;

    private Integer sortOrder;

    private LocalDateTime updateTime;
}
