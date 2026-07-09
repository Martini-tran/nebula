package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台空间标签创建请求
 */
@Data
public class SpaceTagCreateRequest {

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100")
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
