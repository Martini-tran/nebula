package com.nebula.forge.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件版本权限声明VO（管理员端）
 *
 * @author nebula
 */
@Data
public class ForgePluginPermissionAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 权限ID
     */
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
     * 权限编码
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
    private LocalDateTime createTime;
}
