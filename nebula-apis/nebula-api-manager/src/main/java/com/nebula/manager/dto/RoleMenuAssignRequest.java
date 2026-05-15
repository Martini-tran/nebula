package com.nebula.manager.dto;

import java.util.List;
import lombok.Data;

/**
 * 角色菜单分配请求DTO
 */
@Data
public class RoleMenuAssignRequest {

    /**
     * 菜单ID列表
     */
    private List<Long> menuIds;
}
