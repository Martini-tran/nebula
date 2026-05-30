package com.nebula.space.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台目录视图对象（树形）
 */
@Data
public class FolderAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目录ID
     */
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 父目录ID
     */
    private Long parentId;

    /**
     * 祖级列表
     */
    private String ancestors;

    /**
     * 目录名称
     */
    private String name;

    /**
     * 目录层级
     */
    private Integer level;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 来源
     */
    private String source;

    /**
     * 外部来源标识
     */
    private String sourceKey;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 子目录
     */
    private List<FolderAdminVO> children;
}
