package com.nebula.manager.vo;

import lombok.Data;

/**
 * 菜单元信息VO
 */
@Data
public class MenuMetaVO {

    /**
     * 菜单标题
     */
    private String title;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 激活状态图标
     */
    private String activeIcon;

    /**
     * 排序序号
     */
    private Integer order;

    /**
     * 是否缓存页面
     */
    private Boolean keepAlive;

    /**
     * 是否固定标签页
     */
    private Boolean affixTab;

    /**
     * 是否在菜单中隐藏
     */
    private Boolean hideInMenu;

    /**
     * 是否在菜单中隐藏子菜单
     */
    private Boolean hideChildrenInMenu;

    /**
     * 是否在面包屑中隐藏
     */
    private Boolean hideInBreadcrumb;

    /**
     * 是否在标签页中隐藏
     */
    private Boolean hideInTab;

    /**
     * 激活路径
     */
    private String activePath;

    /**
     * 外链地址
     */
    private String link;

    /**
     * 内嵌页面地址
     */
    private String iframeSrc;

    /**
     * 徽章类型
     */
    private String badgeType;

    /**
     * 徽章内容
     */
    private String badge;

    /**
     * 徽章样式变体
     */
    private String badgeVariants;
}
