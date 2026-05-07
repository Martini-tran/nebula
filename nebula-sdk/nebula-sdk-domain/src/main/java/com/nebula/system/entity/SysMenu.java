package com.nebula.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单与权限表
 *
 * @author nebula
 */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 父菜单ID，0表示根节点
     */
    private Long parentId;

    /**
     * 类型：1目录 2菜单 3按钮 4内嵌 5外链
     */
    private Integer menuType;

    /**
     * 名称（meta.title 来源，可为 i18n key）
     */
    private String menuName;

    /**
     * 路由名（vue-router name），跨菜单唯一
     */
    private String routeName;

    /**
     * 前端路由路径（如 /user）
     */
    private String path;

    /**
     * 前端组件路径（如 system/user/index）
     */
    private String component;

    /**
     * 权限标识（如 user:list, user:delete）
     */
    private String perms;

    /**
     * 图标
     */
    private String icon;

    /**
     * 激活态图标
     */
    private String activeIcon;

    /**
     * 高亮指定路径（type=embedded/menu 时生效）
     */
    private String activePath;

    /**
     * 外链 / 内嵌地址（type=link 用作 link，type=embedded 用作 iframeSrc）
     */
    private String linkUrl;

    /**
     * 是否缓存：1是 0否
     */
    private Integer keepAlive;

    /**
     * 是否固定 tab：1是 0否
     */
    private Integer affixTab;

    /**
     * 是否在菜单隐藏：1是 0否
     */
    private Integer hideInMenu;

    /**
     * 是否隐藏子菜单：1是 0否
     */
    private Integer hideChildrenInMenu;

    /**
     * 是否在面包屑隐藏：1是 0否
     */
    private Integer hideInBreadcrumb;

    /**
     * 是否在多页签隐藏：1是 0否
     */
    private Integer hideInTab;

    /**
     * 徽章类型：dot / normal
     */
    private String badgeType;

    /**
     * 徽章文本（badge_type=normal 时生效）
     */
    private String badge;

    /**
     * 徽章样式：default / destructive / primary / success / warning
     */
    private String badgeVariants;

    /**
     * 排序（越小越靠前）
     */
    private Integer sort;

    /**
     * 是否显示：1是 0否
     */
    private Integer visible;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
