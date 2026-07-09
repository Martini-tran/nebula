package com.nebula.forge.entity;

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
 * 插件版本权限声明表
 *
 * @author nebula
 */
@Data
@TableName("forge_plugin_permission")
public class ForgePluginPermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件权限ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 插件版本ID
     */
    private Long versionId;

    /**
     * 权限编码，如 clipboard.read/network.request
     */
    private String permissionCode;

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 权限用途说明
     */
    private String description;

    /**
     * 风险等级：1低 2中 3高
     */
    private Integer riskLevel;

    /**
     * 是否必需：1必需 0可选
     */
    private Integer required;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
