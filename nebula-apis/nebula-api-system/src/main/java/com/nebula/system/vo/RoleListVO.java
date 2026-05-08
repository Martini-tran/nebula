package com.nebula.system.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色列表行 / 详情
 *
 * @author nebula
 */
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
