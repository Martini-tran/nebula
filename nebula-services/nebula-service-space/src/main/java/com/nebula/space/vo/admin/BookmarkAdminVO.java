package com.nebula.space.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台书签视图对象
 */
@Data
public class BookmarkAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 书签ID
     */
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 所属目录ID
     */
    private Long folderId;

    /**
     * 书签标题
     */
    private String title;

    /**
     * 原始URL
     */
    private String url;

    /**
     * 规范化URL
     */
    private String normalizedUrl;

    /**
     * URL哈希
     */
    private String urlHash;

    /**
     * 域名
     */
    private String domain;

    /**
     * 描述
     */
    private String description;

    /**
     * 站点图标URL
     */
    private String faviconUrl;

    /**
     * 站点图标文件ID
     */
    private Long faviconFileId;

    /**
     * 来源
     */
    private String source;

    /**
     * 外部来源标识
     */
    private String sourceKey;

    /**
     * 状态：0正常 1归档 2失效
     */
    private Integer status;

    /**
     * 访问次数
     */
    private Integer visitCount;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastVisitTime;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    private String remark;

    /**
     * 关联的标签
     */
    private List<SpaceTagAdminVO> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
