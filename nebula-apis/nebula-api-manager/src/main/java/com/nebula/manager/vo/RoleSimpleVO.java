package com.nebula.manager.vo;

import lombok.Data;

/**
 * 角色简要VO
 */
@Data
public class RoleSimpleVO {

    /**
     * 角色ID
     */
    private Long id;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;
}
