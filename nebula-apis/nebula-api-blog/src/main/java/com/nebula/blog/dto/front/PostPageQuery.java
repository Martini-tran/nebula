package com.nebula.blog.dto.front;

import lombok.Data;

@Data
public class PostPageQuery {

    private Long categoryId;
    private Long tagId;
    private String keyword;
    private String cursor;
    private Integer limit = 10;
}
