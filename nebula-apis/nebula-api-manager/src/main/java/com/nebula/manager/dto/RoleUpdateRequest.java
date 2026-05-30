package com.nebula.manager.dto;

import lombok.Data;

/**
 * 角色更新请求DTO
 */
@Data
public class RoleUpdateRequest {

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
