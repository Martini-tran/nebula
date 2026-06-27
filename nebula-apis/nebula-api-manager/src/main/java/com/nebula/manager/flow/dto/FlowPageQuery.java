package com.nebula.manager.flow.dto;

import lombok.Data;

/**
 * AI流程分页查询参数
 *
 * @author nebula
 */
@Data
public class FlowPageQuery {

    /**
     * 关键字（流程编码或名称模糊匹配）
     */
    private String keyword;

    /**
     * 状态过滤：1启用 0禁用
     */
    private Integer status;

    /**
     * 页码，从1开始
     */
    private Integer pageNum;

    /**
     * 每页条数
     */
    private Integer pageSize;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        return pageSize == null || pageSize < 1 || pageSize > 200 ? 10 : pageSize;
    }
}
