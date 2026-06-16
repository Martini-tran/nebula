package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件权限声明项
 *
 * @author nebula
 */
@Data
public class ForgePluginPermissionItem {

    /**
     * 权限编码，如 clipboard.read/network.request
     */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    private String permissionCode;

    /**
     * 权限名称
     */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String permissionName;

    /**
     * 权限用途说明
     */
    @Size(max = 500, message = "权限说明长度不能超过500")
    private String description;

    /**
     * 风险等级：1低 2中 3高
     */
    private Integer riskLevel = 1;

    /**
     * 是否必需：1必需 0可选
     */
    private Integer required = 1;
}
