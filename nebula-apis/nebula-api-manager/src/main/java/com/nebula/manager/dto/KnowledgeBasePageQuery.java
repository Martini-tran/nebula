package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeBasePageQuery extends PageQuery {

    /**
     * 关键词（编码/名称）
     */
    private String keyword;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;
}
