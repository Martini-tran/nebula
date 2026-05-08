package com.nebula.manager.dto;

import lombok.Data;

@Data
public class RoleCreateRequest {

    private String roleCode;
    private String roleName;
    private Integer status;
    private String remark;
}
