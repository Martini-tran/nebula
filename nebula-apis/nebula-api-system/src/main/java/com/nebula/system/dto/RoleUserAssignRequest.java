package com.nebula.system.dto;

import java.util.List;
import lombok.Data;

/**
 * 角色 - 用户关联入参（全量覆盖）
 *
 * @author nebula
 */
@Data
public class RoleUserAssignRequest {

    /**
     * 当前角色应绑定的用户 ID 列表
     */
    private List<Long> userIds;
}
