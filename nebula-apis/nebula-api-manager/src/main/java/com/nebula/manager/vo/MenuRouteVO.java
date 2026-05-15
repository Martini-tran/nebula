package com.nebula.manager.vo;

import java.util.List;
import lombok.Data;

/**
 * 菜单路由VO
 */
@Data
public class MenuRouteVO {

    /**
     * 路由名称
     */
    private String name;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 菜单元信息
     */
    private MenuMetaVO meta;

    /**
     * 子路由列表
     */
    private List<MenuRouteVO> children;
}
