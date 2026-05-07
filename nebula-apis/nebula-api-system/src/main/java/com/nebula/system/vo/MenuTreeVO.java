package com.nebula.system.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 菜单管理列表 / 树节点
 * 对齐前端 web-ele/src/api/system/menu 的 SystemMenuApi.SystemMenu
 * 与 MenuRouteVO 区别：保留 id/pid/type/authCode/status 等管理字段，
 * 类型字段使用前端字符串（catalog/menu/button/embedded/link）
 *
 * @author nebula
 */
@Data
public class MenuTreeVO {

    /**
     * 菜单ID
     */
    private Long id;

    /**
     * 父菜单ID（0 表示根节点）
     */
    private Long pid;

    /**
     * 路由名（vue-router name）
     */
    private String name;

    /**
     * 菜单类型：catalog / menu / button / embedded / link
     */
    private String type;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径（type=menu 时为视图路径，type=catalog 时可填 layout 名）
     */
    private String component;

    /**
     * 权限标识（对应 sys_menu.perms）
     */
    private String authCode;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    /**
     * 高亮指定路径
     */
    private String activePath;

    /**
     * 路由 meta
     */
    private MenuMetaVO meta;

    /**
     * 子菜单
     */
    private List<MenuTreeVO> children;

    /**
     * 排序
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
