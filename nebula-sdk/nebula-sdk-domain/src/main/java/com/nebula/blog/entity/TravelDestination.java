package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅游目的地表（层级结构）
 *
 * @author nebula
 */
@Data
@TableName("travel_destination")
public class TravelDestination implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目的地ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父级目的地ID（如：中国 -> 云南 -> 大理）
     */
    private Long parentId;

    /**
     * 名称（如：大理古城）
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
     * 封面图（关联blog_file_asset）
     */
    private Long coverFileId;

    /**
     * 经纬度（MySQL spatial类型）
     */
    private String location;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 访问/打卡次数
     */
    private Integer visitCount;

    /**
     * 评分（0.0-5.0）
     */
    private BigDecimal rating;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}