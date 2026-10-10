package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API Key 分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiKeyPageQuery extends PageQuery {

    /**
     * 名称关键词
     */
    private String name;

    /**
     * 状态：0=禁用 1=启用
     */
    private Integer status;
}
