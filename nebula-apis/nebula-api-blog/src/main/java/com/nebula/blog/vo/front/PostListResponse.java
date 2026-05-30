package com.nebula.blog.vo.front;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文章列表响应VO（前端）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostListResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章列表
     */
    private List<PostListVO> items;

    /**
     * 下一页游标
     */
    private String nextCursor;
}
