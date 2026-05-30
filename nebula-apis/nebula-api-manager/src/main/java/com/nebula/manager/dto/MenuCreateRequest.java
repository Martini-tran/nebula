package com.nebula.manager.dto;

import com.nebula.manager.vo.MenuMetaVO;
import lombok.Data;

/**
 * 菜单创建请求DTO
 */
@Data
public class MenuCreateRequest {

    /**
     * 父菜单ID
     */
    private Long pid;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 菜单类型
     */
    private String type;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 权限编码
     */
    private String authCode;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 激活路径
     */
    private String activePath;

    /**
     * 排序序号
     */
    private Integer sort;

    /**
     * 备注
     */
    private String remark;

    /**
     * 菜单元信息
     */
    private MenuMetaVO meta;
}
