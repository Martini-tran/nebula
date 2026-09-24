package com.nebula.scribe.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 作品列表项，字段对齐前端 {@code WorkListItem}
 */
@Data
public class WorkListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    /**
     * 一句话简介
     */
    private String summary;

    /**
     * 封面地址（封面上传未接入前恒为 null）
     */
    private String coverUrl;

    private String audience;

    private String genre;

    private List<String> tags;

    private List<String> protagonists;

    private String status;

    private Integer wordCount;

    private Integer chapterCount;

    private Integer targetWordCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
