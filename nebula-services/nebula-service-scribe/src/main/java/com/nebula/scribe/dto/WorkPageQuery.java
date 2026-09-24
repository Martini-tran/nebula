package com.nebula.scribe.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的作品分页查询，字段对齐前端 {@code WorkPageQuery}
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPageQuery extends PageQuery {

    /**
     * 关键词，匹配标题/一句话简介
     */
    private String keyword;

    /**
     * 状态：draft/serializing/paused/finished
     */
    private String status;

    /**
     * 排序：recent 最近编辑（默认）/created 创建时间/words 字数/title 标题
     */
    private String sort;
}
