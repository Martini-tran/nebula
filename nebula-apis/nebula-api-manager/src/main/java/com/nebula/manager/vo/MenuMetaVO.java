package com.nebula.manager.vo;

import lombok.Data;

@Data
public class MenuMetaVO {

    private String title;
    private String icon;
    private String activeIcon;
    private Integer order;
    private Boolean keepAlive;
    private Boolean affixTab;
    private Boolean hideInMenu;
    private Boolean hideChildrenInMenu;
    private Boolean hideInBreadcrumb;
    private Boolean hideInTab;
    private String activePath;
    private String link;
    private String iframeSrc;
    private String badgeType;
    private String badge;
    private String badgeVariants;
}
