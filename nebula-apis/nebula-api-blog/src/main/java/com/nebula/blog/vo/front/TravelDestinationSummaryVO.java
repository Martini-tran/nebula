package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 旅游目的地简要VO（前台）
 */
@Data
public class TravelDestinationSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String slug;

    /**
     * 类型：0国家 1省份/州 2城市 3景点/POI
     */
    private Integer type;
}
