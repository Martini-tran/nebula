package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI MCP 服务器分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class McpServerPageQuery extends PageQuery {

    /**
     * 关键词（服务器编码/名称/地址）
     */
    private String keyword;

    /**
     * 传输类型：stdio/sse/streamable-http
     */
    private String transport;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;
}
