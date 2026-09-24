package com.nebula.scribe.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 章节详情（含正文），字段对齐前端 {@code ChapterDetail}
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChapterDetailVO extends ChapterListVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 正文，纯文本
     */
    private String content;

    /**
     * 修订号，下次保存时原样带回
     */
    private Long revision;
}
