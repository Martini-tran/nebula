package com.nebula.manager.dto;

import lombok.Data;

/**
 * 角色创建请求DTO
 */
@Data
public class RoleCreateRequest {

    /**
     * 角色编码
     */
    private String roleCode;

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
