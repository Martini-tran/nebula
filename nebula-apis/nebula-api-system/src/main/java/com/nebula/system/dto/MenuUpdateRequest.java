package com.nebula.system.dto;

import com.nebula.system.vo.MenuMetaVO;
import lombok.Data;

/**
 * 更新菜单入参
 * 字段语义与 MenuCreateRequest 一致；null 字段表示不更新
 *
 * @author nebula
 */
@Data
public class MenuUpdateRequest {

    /**
     * 父菜单ID，传 0 表示置为根节点
     */
    private Long pid;

    /**
     * 路由名（vue-router name），跨菜单唯一
     */
    private String name;

    /**
     * 菜单类型：catalog / menu / button / embedded / link
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
     * 权限标识
     */
    private String authCode;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    /**
     * 高亮指定路径
     */
    private String activePath;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 备注
     */
    private String remark;

    /**
     * 路由 meta
     */
    private MenuMetaVO meta;
}
