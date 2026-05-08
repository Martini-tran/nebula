package com.nebula.manager.dto;

import lombok.Data;

@Data
public class RoleUpdateRequest {

    private String roleName;
    private Integer status;
    private String remark;
}
