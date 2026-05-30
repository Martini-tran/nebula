package com.nebula.manager.dto;

import lombok.Data;

/**
 * 角色状态更新请求DTO
 */
@Data
public class RoleStatusUpdateRequest {

    /**
     * 状态
     */
    private Integer status;
}
