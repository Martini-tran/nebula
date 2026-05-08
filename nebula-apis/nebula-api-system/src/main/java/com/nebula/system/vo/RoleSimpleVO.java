package com.nebula.system.vo;

import lombok.Data;

/**
 * 角色精简结构（下拉、勾选框等场景使用）
 *
 * @author nebula
 */
@Data
public class RoleSimpleVO {

    private Long id;
    private String roleCode;
    private String roleName;
}
