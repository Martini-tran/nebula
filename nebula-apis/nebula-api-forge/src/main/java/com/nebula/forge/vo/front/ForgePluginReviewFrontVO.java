package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件评价VO（前台，仅展示中的评价）
 *
 * @author nebula
 */
@Data
public class ForgePluginReviewFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评价ID */
    private Long id;

    /** 插件ID */
    private Long pluginId;

    /** 评价用户ID */
    private Long userId;

    /** 评价时安装的版本ID */
    private Long versionId;

    /** 评分：1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 作者/管理员回复 */
    private String replyContent;

    /** 回复时间 */
    private LocalDateTime replyTime;

    /** 点赞数 */
    private Integer likeCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
