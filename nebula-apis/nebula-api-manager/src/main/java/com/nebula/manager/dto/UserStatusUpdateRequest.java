package com.nebula.manager.dto;

import lombok.Data;

/**
 * 用户状态更新请求DTO
 */
@Data
public class UserStatusUpdateRequest {

    /**
     * 状态
     */
    private Integer status;
}
