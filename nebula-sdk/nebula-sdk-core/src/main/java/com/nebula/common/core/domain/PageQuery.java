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
     * 前端传入的页码参数，表示当前请求的是第几页的数据
     * 如果传入的值小于1或为null，则使用默认页码(DEFAULT_PAGE_NUM)
     */
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页大小
     * 前端传入的每页显示记录数，表示每页返回多少条数据
     * 如果传入的值小于1或为null，则使用默认每页大小(DEFAULT_PAGE_SIZE)
     * 实际使用的值不会超过最大每页大小(MAX_PAGE_SIZE)
     */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 排序字段
     * 前端传入的排序规则，用于数据库查询时指定排序方式
     * 格式通常为 "字段名 排序方向"，如 "create_time desc", "id asc" 等
     * 如果未传入此参数，则按数据库默认顺序返回数据
     */
    private String orderBy;

    /**
     * 获取安全的页码值
     * 检查传入的页码是否有效，如果无效则返回默认页码
     *
     * @return 返回有效的页码值，最小为1
     */
    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 获取安全的每页大小值
     * 检查传入的每页大小是否有效，如果无效则返回默认每页大小
     * 同时限制每页大小不超过最大限制值
     *
     * @return 返回有效的每页大小值，在[1, MAX_PAGE_SIZE]范围内
     */
    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
