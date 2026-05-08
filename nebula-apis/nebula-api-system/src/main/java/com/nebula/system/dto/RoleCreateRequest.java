package com.nebula.system.dto;

import lombok.Data;

/**
 * 角色创建入参
 *
 * @author nebula
 */
@Data
public class RoleCreateRequest {

    /**
     * 角色编码（唯一，仅字母 / 数字 / 下划线 / 短横线）
     */
    private String roleCode;

    /**
     * 角色名称（展示用，唯一）
     */
    private String roleName;

    /**
     * 状态：1正常 0禁用，缺省 1
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
