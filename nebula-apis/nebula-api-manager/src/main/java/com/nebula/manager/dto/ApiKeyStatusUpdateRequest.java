package com.nebula.manager.dto;

import lombok.Data;

/**
 * API Key 状态更新请求DTO
 */
@Data
public class ApiKeyStatusUpdateRequest {

    /**
     * 状态：0=禁用 1=启用
     */
    private Integer status;
}
