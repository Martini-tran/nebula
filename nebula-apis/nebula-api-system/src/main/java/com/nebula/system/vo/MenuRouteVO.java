package com.nebula.system.vo;

import java.util.List;
import lombok.Data;

/**
 * 后端菜单转出的路由结构，对齐前端 RouteRecordStringComponent
 * - component 是字符串：layout 名（如 "BasicLayout"）或视图相对路径（如 "system/user/index"）
 * - 顶级路由 component 留 "BasicLayout"，access.ts 在挂载到 root 时会主动 delete 掉
 *
 * @author nebula
 */
@Data
public class MenuRouteVO {

    /**
     * Vue Router name，由 path 派生，跨菜单唯一
     */
    private String name;

    /**
     * 路由路径（绝对路径，如 /system/user）
     */
    private String path;

    /**
     * 组件字符串：layoutMap 名或视图路径
     */
    private String component;

    /**
     * 路由 meta
     */
    private MenuMetaVO meta;

    /**
     * 子路由
     */
    private List<MenuRouteVO> children;
}
