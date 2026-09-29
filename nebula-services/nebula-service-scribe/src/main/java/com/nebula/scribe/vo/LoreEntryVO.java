package com.nebula.scribe.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设定条目，字段对齐前端 {@code LoreEntry}；列表接口不带 detail（为 null），点开详情再取
 */
@Data
public class LoreEntryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long workId;

    /**
     * 类型：character/location/faction/item/rule
     */
    private String kind;

    private String name;

    private List<String> aliases;

    /**
     * 一句话概述
     */
    private String summary;

    /**
     * 详细设定，Markdown
     */
    private String detail;

    private List<String> tags;

    /**
     * AI 生成时是否默认带上
     */
    private Boolean pinned;

    private LocalDateTime updateTime;
}
