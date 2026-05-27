package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅游目的地VO（管理员端）
 */
@Data
public class TravelDestinationAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    private String name;

    private String slug;

    /**
     * 类型：0国家 1省份/州 2城市 3景点/POI
     */
    private Integer type;

    private String description;

    private Long coverFileId;

    private String coverUrl;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    private String address;

    private Integer visitCount;

    private BigDecimal rating;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 子节点列表
     */
    private List<TravelDestinationAdminVO> children;
}
