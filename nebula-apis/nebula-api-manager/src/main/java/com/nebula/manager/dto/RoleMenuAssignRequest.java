package com.nebula.manager.dto;

import java.util.List;
import lombok.Data;

@Data
public class RoleMenuAssignRequest {

    private List<Long> menuIds;
}
