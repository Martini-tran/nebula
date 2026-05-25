package com.nebula.blog.vo.admin;

import com.nebula.blog.vo.front.CategorySummaryVO;
import com.nebula.blog.vo.front.TagSummaryVO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台文章详情/列表视图对象
 */
@Data
public class PostAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 标题
     */
    private String title;

    /**
     * 别名
     */
    private String slug;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 正文文件ID
     */
    private Long contentFileId;

    /**
     * Markdown正文内容
     */
    private String content;

    /**
     * 正文文件URL
     */
    private String contentUrl;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 封面URL
     */
    private String coverUrl;

    /**
     * 文章状态
     */
    private String status;

    /**
     * 可见性
     */
    private String visibility;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 是否原创
     */
    private Boolean isOriginal;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 点赞量
     */
    private Integer likeCount;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 分类列表
     */
    private List<CategorySummaryVO> categories;

    /**
     * 标签列表
     */
    private List<TagSummaryVO> tags;
}
