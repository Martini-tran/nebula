package com.nebula.blog.vo.front;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章内容VO（前端）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostContentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章内容（Markdown格式）
     */
    private String content;
}
