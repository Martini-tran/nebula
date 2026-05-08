package com.nebula.system.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询入参
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQuery extends PageQuery {

    /**
     * 角色编码（模糊匹配）
     */
    private String roleCode;

    /**
     * 角色名称（模糊匹配）
     */
    private String roleName;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;
}
