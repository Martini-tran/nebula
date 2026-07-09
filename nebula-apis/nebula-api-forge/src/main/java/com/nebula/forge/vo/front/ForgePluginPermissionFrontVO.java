package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 插件权限声明VO（前台）
 *
 * @author nebula
 */
@Data
public class ForgePluginPermissionFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限编码，如 clipboard.read */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 权限用途说明 */
    private String description;

    /** 风险等级：1低 2中 3高 */
    private Integer riskLevel;

    /** 是否必需：1必需 0可选 */
    private Integer required;
}
