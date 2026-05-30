package com.nebula.blog.vo.front;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 系列列表分页响应（基于游标）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeriesListResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页系列列表
     */
    private List<SeriesListVO> items;

    /**
     * 下一页游标
     */
    private String nextCursor;
}
