package com.nebula.system.vo;

import lombok.Data;

/**
 * 路由 meta 对应前端 RouteMeta（仅暴露后端能填的字段，其余前端默认值）
 *
 * @author nebula
 */
@Data
public class MenuMetaVO {

    /**
     * 标题
     */
    private String title;

    /**
     * 图标 key（如 lucide:layout-dashboard）
     */
    private String icon;

    /**
     * 排序，越小越靠前
     */
    private Integer order;
}
