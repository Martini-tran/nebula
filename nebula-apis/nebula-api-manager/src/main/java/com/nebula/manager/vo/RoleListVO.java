package com.nebula.manager.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RoleListVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
