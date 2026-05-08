package com.nebula.system.dto;

import lombok.Data;

/**
 * 角色状态切换入参
 *
 * @author nebula
 */
@Data
public class RoleStatusUpdateRequest {

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;
}
