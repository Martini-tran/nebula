package com.nebula.system.vo;

import lombok.Data;

/**
 * 路由 meta，对齐前端 RouteMeta（packages/@core/base/typings/src/vue-router.d.ts）
 * 仅暴露后端能填的字段，未配置的为 null
 *
 * @author nebula
 */
@Data
public class MenuMetaVO {

    /**
     * 标题（可为 i18n key）
     */
    private String title;

    /**
     * 图标 key（如 lucide:layout-dashboard）
     */
    private String icon;

    /**
     * 激活态图标
     */
    private String activeIcon;

    /**
     * 排序，越小越靠前
     */
    private Integer order;

    /**
     * 是否缓存
     */
    private Boolean keepAlive;

    /**
     * 是否固定 tab
     */
    private Boolean affixTab;

    /**
     * 是否在菜单中隐藏
     */
    private Boolean hideInMenu;

    /**
     * 是否隐藏子菜单
     */
    private Boolean hideChildrenInMenu;

    /**
     * 是否在面包屑中隐藏
     */
    private Boolean hideInBreadcrumb;

    /**
     * 是否在多页签中隐藏
     */
    private Boolean hideInTab;

    /**
     * 高亮指定路径
     */
    private String activePath;

    /**
     * 外链地址（type=link）
     */
    private String link;

    /**
     * 内嵌 iframe 地址（type=embedded）
     */
    private String iframeSrc;

    /**
     * 徽章类型：dot / normal
     */
    private String badgeType;

    /**
     * 徽章文本
     */
    private String badge;

    /**
     * 徽章样式：default / destructive / primary / success / warning
     */
    private String badgeVariants;
}
