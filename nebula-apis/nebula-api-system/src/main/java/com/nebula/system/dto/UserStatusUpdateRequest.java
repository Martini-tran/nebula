package com.nebula.system.dto;

import lombok.Data;

/**
 * 用户启用 / 禁用入参
 *
 * @author nebula
 */
@Data
public class UserStatusUpdateRequest {

    /**
     * 目标状态：1正常 0禁用
     */
    private Integer status;
}
