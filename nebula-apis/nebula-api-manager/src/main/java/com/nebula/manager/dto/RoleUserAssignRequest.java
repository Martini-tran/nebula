package com.nebula.manager.dto;

import java.util.List;
import lombok.Data;

/**
 * 角色用户分配请求DTO
 */
@Data
public class RoleUserAssignRequest {

    /**
     * 用户ID列表
     */
    private List<Long> userIds;
}
