package com.nebula.system.dto;

import com.nebula.system.vo.MenuMetaVO;
import lombok.Data;

/**
 * 创建菜单入参
 * 对齐前端 web-ele/src/views/system/menu/modules/form.vue 提交体
 *
 * @author nebula
 */
@Data
public class MenuCreateRequest {

    /**
     * 父菜单ID，0 / null 表示根节点
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
     * 路由路径（catalog/menu/embedded 必填）
     */
    private String path;

    /**
     * 组件路径（type=menu 必填）
     */
    private String component;

    /**
     * 权限标识（type=button 必填）
     */
    private String authCode;

    /**
     * 状态：1正常 0禁用，缺省 1
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
     * 路由 meta（title 必填，其余可选）
     */
    private MenuMetaVO meta;
}
