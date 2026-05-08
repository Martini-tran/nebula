package com.nebula.system.dto;

import lombok.Data;

/**
 * 角色更新入参（roleCode 不可修改）
 *
 * @author nebula
 */
@Data
public class RoleUpdateRequest {

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
