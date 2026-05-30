package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 旅游目的地更新请求
 */
@Data
public class TravelDestinationUpdateRequest {

    /**
     * 父级目的地ID（传 0 视为提升为顶层；不传不变）
     */
    private Long parentId;

    /**
     * 名称
     */
    private String name;

    /**
     * URL标识
     */
    private String slug;

    /**
     * 类型：0国家 1省份/州 2城市 3景点/POI
     */
    private Integer type;

    /**
     * 描述
     */
    private String description;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 是否清空封面（前端显式信号）
     */
    private Boolean clearCoverFileId;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;

    /**
     * 排序序号
     */
    private Integer sortOrder;
}
