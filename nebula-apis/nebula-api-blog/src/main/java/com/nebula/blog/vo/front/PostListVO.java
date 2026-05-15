package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String slug;
    private String title;
    private String summary;
    private String coverUrl;
    private List<CategorySummaryVO> categories;
    private List<TagSummaryVO> tags;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime publishedAt;
}
