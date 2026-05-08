package com.nebula.manager.dto;

import java.util.List;
import lombok.Data;

@Data
public class RoleUserAssignRequest {

    private List<Long> userIds;
}
