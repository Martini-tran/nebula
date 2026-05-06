package com.nebula.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
     * 类型：1目录 2菜单 3按钮/接口
     */
    private Integer menuType;

    /**
     * 名称
     */
    private String menuName;

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
