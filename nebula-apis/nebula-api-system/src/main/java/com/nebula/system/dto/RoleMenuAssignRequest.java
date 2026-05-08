package com.nebula.system.dto;

import java.util.List;
import lombok.Data;

/**
 * 角色 - 菜单关联入参（全量覆盖）
 *
 * @author nebula
 */
@Data
public class RoleMenuAssignRequest {

    /**
     * 当前角色应授权的菜单 ID 列表（含目录、菜单、按钮等所有类型）
     */
    private List<Long> menuIds;
}
