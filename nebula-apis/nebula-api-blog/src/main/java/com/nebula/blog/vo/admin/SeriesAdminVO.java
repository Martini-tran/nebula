package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系列管理VO（管理员端）
 */
@Data
public class SeriesAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 系列ID
     */
    private Long id;

    /**
     * 系列名称
     */
    private String name;

    /**
     * 系列URL标识
     */
    private String slug;

    /**
     * 系列简介
     */
    private String description;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 封面URL
     */
    private String coverUrl;

    /**
     * 状态
     */
    private String status;

    /**
     * 可见性
     */
    private String visibility;

    /**
     * 是否完结
     */
    private Boolean isFinished;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 创建者ID
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
