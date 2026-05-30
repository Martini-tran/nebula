package com.nebula.manager.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 菜单树VO
 */
@Data
public class MenuTreeVO {

    /**
     * 菜单ID
     */
    private Long id;

    /**
     * 父菜单ID
     */
    private Long pid;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 菜单类型
     */
    private String type;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 权限编码
     */
    private String authCode;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 激活路径
     */
    private String activePath;

    /**
     * 菜单元信息
     */
    private MenuMetaVO meta;

    /**
     * 子菜单列表
     */
    private List<MenuTreeVO> children;

    /**
     * 排序序号
     */
    private Integer sort;

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
}
