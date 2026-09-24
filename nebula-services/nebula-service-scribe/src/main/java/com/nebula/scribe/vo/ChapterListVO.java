package com.nebula.scribe.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 章节列表项（不含正文），字段对齐前端 {@code ChapterListItem}
 */
@Data
public class ChapterListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long workId;

    /**
     * 所属卷（卷表落地前恒为 null）
     */
    private Long volumeId;

    private String title;

    private Integer sortOrder;

    private String status;

    private Integer wordCount;

    /**
     * 本章梗概
     */
    private String synopsis;

    private LocalDateTime updateTime;
}
