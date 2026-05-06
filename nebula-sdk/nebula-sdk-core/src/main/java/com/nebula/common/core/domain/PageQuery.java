package com.nebula.common.core.domain;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 通用分页请求参数
 * controller 接收前端传入的 pageNum / pageSize / orderBy
 *
 * @author nebula
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 500;

    /**
     * 页码，从 1 开始
     */
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页大小
     */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 排序字段，例如 "create_time desc"
     */
    private String orderBy;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
