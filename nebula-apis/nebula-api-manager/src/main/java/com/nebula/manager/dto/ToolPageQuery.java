package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 工具分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolPageQuery extends PageQuery {

    /**
     * 关键词（工具编码/名称/描述）
     */
    private String keyword;

    /**
     * 分类（http/data/search 等）
     */
    private String category;

    /**
     * 是否启用：0=已下线 1=启用
     */
    private Integer enabled;
}
