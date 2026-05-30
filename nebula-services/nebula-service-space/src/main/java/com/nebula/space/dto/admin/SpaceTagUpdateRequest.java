package com.nebula.space.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台空间标签更新请求
 */
@Data
public class SpaceTagUpdateRequest {

    /**
     * 标签名称
     */
    @Size(max = 100)
    private String name;

    /**
     * 标签颜色
     */
    @Size(max = 32)
    private String color;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    @Size(max = 500)
    private String remark;
}
